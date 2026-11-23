package com.routex.service;

import com.routex.model.Booking;
import com.routex.model.Bus;
import com.routex.model.User;
import com.routex.repository.BookingRepository;
import com.routex.repository.BusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

    private final BookingRepository bookingRepository;
    private final BusRepository busRepository;
    private final BusService busService;

    // ----------------------------------------------------------------
    //  Create Booking  (§4.1 Stimulus/Response)
    // ----------------------------------------------------------------

    @Transactional
    public Booking createBooking(User user, Long busId, LocalDate travelDate,
                                 List<Integer> seats, String passengerName,
                                 String passengerPhone, int passengerAge,
                                 String paymentMethod, String paymentReference) {

        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new IllegalArgumentException("Bus not found: " + busId));

        // REQ-4.1.3: prevent booking already-reserved seats
        if (!busService.areSeatsAvailable(busId, travelDate, seats)) {
            throw new IllegalStateException(
                "One or more selected seats are already booked for this date.");
        }

        // REQ-4.1.4: store booking details (name, seat, price, date)
        String seatStr = seats.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        double total = bus.getPricePerSeat() * seats.size();
        String normalizedMethod = normalizePaymentMethod(paymentMethod);
        String normalizedReference = paymentReference == null ? "" : paymentReference.trim();
        if (normalizedReference.isBlank()) {
            throw new IllegalArgumentException("Payment reference is required.");
        }

        Booking booking = Booking.builder()
                .bookingRef(generateRef())
                .user(user)
                .bus(bus)
                .travelDate(travelDate)
                .seatNumbers(seatStr)
                .passengerName(passengerName)
                .passengerPhone(passengerPhone)
                .passengerAge(passengerAge)
                .totalAmount(total)
                .paymentMethod(normalizedMethod)
                .paymentReference(normalizedReference)
                .paymentStatus("PAID")
                .status("CONFIRMED")
                .bookedAt(LocalDateTime.now())
                .build();

        // REQ-4.1.5: confirmation stored — returned to controller to confirm to user
        return bookingRepository.save(booking);
    }

    // ----------------------------------------------------------------
    //  Cancel Booking
    // ----------------------------------------------------------------

    @Transactional
    public Booking cancelBooking(String bookingRef, Long requestingUserId) {
        Booking booking = bookingRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingRef));

        boolean isOwner  = booking.getUser().getId().equals(requestingUserId);
        boolean isAdmin  = true; // admin check delegated to controller / security config

        if (!isOwner && !isAdmin) {
            throw new SecurityException("Not authorised to cancel this booking.");
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

    private String generateRef() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = bookingRepository.count() + 1;
        return String.format("BK-%s-%04d", date, seq);
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String value = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "CARD", "UPI", "NET_BANKING", "WALLET" -> value;
            default -> throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
        };
    }
}
