package com.routex.service;

import com.routex.model.Booking;
import com.routex.model.Bus;
import com.routex.model.User;
import com.routex.repository.BookingRepository;
import com.routex.repository.BusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Business Logic Layer — Booking operations and Bill Generation.
 *
 * Implements:
 *   §4.1 Stimulus/Response (REQ-4.1.3 to REQ-4.1.5)
 *   §4.3 Bill Generation (REQ-4.3.1, REQ-4.3.2)
 *   §5.2 REQ-5.2.1: prevents data loss via @Transactional
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    public static final int MAX_SEATS_PER_BOOKING = 6;

    private final BookingRepository bookingRepository;
    private final BusRepository busRepository;
    private final BusService busService;

    // ----------------------------------------------------------------
    //  Create Booking  (§4.1 Stimulus/Response)
    // ----------------------------------------------------------------

    /**
     * Books seats. The price is always computed here from the bus, never taken from the caller, and the
     * (simulated) payment reference is generated here too.
     *
     * The bus row is locked first, so concurrent bookings of the same bus run one at a time and the
     * availability check below cannot be raced (REQ-4.1.3).
     */
    @Transactional
    public Booking createBooking(User user, Long busId, LocalDate travelDate,
                                 List<Integer> seats, String passengerName,
                                 String passengerPhone, int passengerAge,
                                 String paymentMethod) {

        Bus bus = busRepository.findByIdForUpdate(busId)
                .orElseThrow(() -> new IllegalArgumentException("Bus not found: " + busId));

        if (travelDate == null || travelDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Travel date cannot be in the past.");
        }
        List<Integer> chosen = validateSeats(bus, seats);

        // REQ-4.1.3: prevent booking already-reserved seats
        if (!busService.areSeatsAvailable(busId, travelDate, chosen)) {
            throw new IllegalStateException(
                "One or more selected seats are already booked for this date.");
        }

        String method = normalizePaymentMethod(paymentMethod);
        double total = bus.getPricePerSeat() * chosen.size();

        // REQ-4.1.4: store booking details (name, seat, price, date)
        Booking booking = Booking.builder()
                .bookingRef("TMP-" + UUID.randomUUID())   // replaced below once the id is known
                .user(user)
                .bus(bus)
                .travelDate(travelDate)
                .seatNumbers(String.join(",", chosen.stream().map(String::valueOf).toList()))
                .passengerName(passengerName.trim())
                .passengerPhone(passengerPhone.trim())
                .passengerAge(passengerAge)
                .totalAmount(total)
                .paymentMethod(method)
                .paymentReference("SIM-" + method + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                .paymentStatus("PAID")
                .status("CONFIRMED")
                .bookedAt(LocalDateTime.now())
                .build();

        booking = bookingRepository.save(booking);
        // reference built from the database id, so two bookings can never share one
        booking.setBookingRef(String.format("BK-%s-%05d",
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), booking.getId()));
        // REQ-4.1.5: confirmation stored — returned to controller to confirm to user
        return bookingRepository.save(booking);
    }

    // ----------------------------------------------------------------
    //  Cancel Booking
    // ----------------------------------------------------------------

    /** Cancels a booking. Only its owner or an administrator may do so. */
    @Transactional
    public Booking cancelBooking(String bookingRef, User requester) {
        Booking booking = bookingRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingRef));
        requireOwnerOrAdmin(booking, requester);
        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new IllegalStateException("This booking is already cancelled.");
        }
        booking.setStatus("CANCELLED");
        return bookingRepository.save(booking);
    }

    // ----------------------------------------------------------------
    //  Queries
    // ----------------------------------------------------------------

    public List<Booking> getBookingsByUser(Long userId) {
        return bookingRepository.findByUserIdOrderByBookedAtDesc(userId);
    }

    public List<Booking> getAllBookings() {
        return bookingRepository.findAllForAdmin();
    }

    public Optional<Booking> findByRef(String ref) {
        return bookingRepository.findByBookingRef(ref);
    }

    /** A booking's details, but only for its owner or an administrator. */
    @Transactional(readOnly = true)
    public Booking getBookingFor(String ref, User requester) {
        Booking booking = bookingRepository.findByBookingRef(ref)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + ref));
        requireOwnerOrAdmin(booking, requester);
        // touch the lazy associations while the session is open
        booking.getBus().getId();
        booking.getUser().getId();
        return booking;
    }

    // ----------------------------------------------------------------
    //  Admin Stats
    // ----------------------------------------------------------------

    public long getTotalBookings()    { return bookingRepository.count(); }
    public long getConfirmedCount()   { return bookingRepository.countByStatus("CONFIRMED"); }
    public Double getTotalRevenue()   { return bookingRepository.getTotalRevenue(); }

    // ----------------------------------------------------------------
    //  Bill Generation  (§4.3 REQ-4.3.1)
    // ----------------------------------------------------------------

    /**
     * Generates a plain-text bill string for a given booking.
     * REQ-4.3.1: bill contains customer name, seat number, price, date.
     */
    public String generateBill(Booking booking) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy");
        Bus bus = booking.getBus();
        return String.format("""
            ============================================================
                          ROUTEX — BOOKING RECEIPT
            ============================================================
            Booking Ref  : %s
            Booked On    : %s
            ============================================================
            Passenger    : %s  (Age: %d)
            Phone        : %s
            ------------------------------------------------------------
            Route        : %s → %s
            Operator     : %s (%s)
            Travel Date  : %s
            Departure    : %s     Arrival: %s
            Seat(s)      : %s
            ------------------------------------------------------------
            Price/Seat   : ₹%.2f × %d seat(s)
            TOTAL AMOUNT : ₹%.2f
            Payment      : %s (%s)
            Payment Ref  : %s
            Status       : %s
            ============================================================
            """,
                booking.getBookingRef(),
                booking.getBookedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")),
                booking.getPassengerName(), booking.getPassengerAge(),
                booking.getPassengerPhone(),
                bus.getFromCity(), bus.getToCity(),
                bus.getOperatorName(), bus.getBusType(),
                booking.getTravelDate().format(df),
                bus.getDepartureTime(), bus.getArrivalTime(),
                booking.getSeatNumbers(),
                bus.getPricePerSeat(), booking.getSeatList().size(),
                booking.getTotalAmount(),
                booking.getPaymentStatus(),
                booking.getPaymentMethod(),
                booking.getPaymentReference(),
                booking.getStatus()
        );
    }

    // ----------------------------------------------------------------
    //  Helpers
    // ----------------------------------------------------------------

    private void requireOwnerOrAdmin(Booking booking, User requester) {
        boolean isOwner = requester != null && booking.getUser().getId().equals(requester.getId());
        boolean isAdmin = requester != null && "ADMIN".equals(requester.getRole());
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You are not allowed to access this booking.");
        }
    }

    /** Seats must be 1..totalSeats, distinct, and at most MAX_SEATS_PER_BOOKING. Returns them sorted. */
    private List<Integer> validateSeats(Bus bus, List<Integer> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("Select at least one seat.");
        }
        List<Integer> distinct = seats.stream().distinct().sorted().toList();
        if (distinct.size() != seats.size()) {
            throw new IllegalArgumentException("The same seat was selected more than once.");
        }
        if (distinct.size() > MAX_SEATS_PER_BOOKING) {
            throw new IllegalArgumentException("Maximum " + MAX_SEATS_PER_BOOKING + " seats per booking.");
        }
        for (Integer seat : distinct) {
            if (seat == null || seat < 1 || seat > bus.getTotalSeats()) {
                throw new IllegalArgumentException("Seat " + seat + " does not exist on this bus.");
            }
        }
        return distinct;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String value = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "CARD", "UPI", "NET_BANKING", "WALLET" -> value;
            default -> throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
        };
    }
}
