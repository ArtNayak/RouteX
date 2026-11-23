package com.routex.controller;

import com.routex.model.Booking;
import com.routex.model.Bus;
import com.routex.model.User;
import com.routex.service.BookingService;
import com.routex.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Presentation Layer — Booking REST endpoints.
 *
 * POST /api/bookings              — create booking (§4.1)
 * GET  /api/bookings              — my bookings (authenticated user)
 * GET  /api/bookings/{ref}        — booking detail + bill (§4.3)
 * DELETE /api/bookings/{ref}      — cancel booking
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final UserService    userService;

    // ---- Request DTO ----

    @Data
    public static class BookingRequest {
        @NotNull  private Long    busId;
        @NotNull  private String  travelDate;      // "yyyy-MM-dd"
        @NotEmpty private List<Integer> seats;
        @NotBlank private String  passengerName;
        @NotBlank private String  passengerPhone;
        @NotNull  @Min(1) @Max(120) private Integer passengerAge;
        @NotBlank private String paymentMethod;
        @NotBlank private String paymentReference;
    }

    // ---- Endpoints ----

    /** REQ-4.1.3 – REQ-4.1.5: create and confirm booking */
    @PostMapping
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody BookingRequest req,
            Authentication auth) {

        User user = resolveUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error","Not authenticated"));

        try {
            LocalDate date = LocalDate.parse(req.getTravelDate());
            Booking booking = bookingService.createBooking(
                    user, req.getBusId(), date, req.getSeats(),
                    req.getPassengerName(), req.getPassengerPhone(), req.getPassengerAge(),
                    req.getPaymentMethod(), req.getPaymentReference());

            return ResponseEntity.ok(bookingToMap(booking));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** My bookings list */
    @GetMapping
    public ResponseEntity<?> myBookings(Authentication auth) {
        User user = resolveUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error","Not authenticated"));
        List<Booking> bookings = bookingService.getBookingsByUser(user.getId());
        return ResponseEntity.ok(bookings.stream().map(this::bookingToMap).toList());
    }

    /** Booking detail + generated bill text (§4.3 REQ-4.3.1) */
    @GetMapping("/{ref}")
    public ResponseEntity<?> getBooking(@PathVariable String ref, Authentication auth) {
        return bookingService.findByRef(ref)
                .map(b -> {
                    Map<String, Object> data = bookingToMap(b);
                    data.put("bill", bookingService.generateBill(b));
                    return ResponseEntity.ok(data);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Cancel booking */
    @DeleteMapping("/{ref}")
    public ResponseEntity<?> cancelBooking(@PathVariable String ref, Authentication auth) {
        User user = resolveUser(auth);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error","Not authenticated"));
        try {
            Booking cancelled = bookingService.cancelBooking(ref, user.getId());
            return ResponseEntity.ok(bookingToMap(cancelled));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ---- Helpers ----

    private User resolveUser(Authentication auth) {
        if (auth == null) return null;
        return userService.findByEmail(auth.getName()).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bookingToMap(Booking b) {
        Bus bus = b.getBus();
        return Map.ofEntries(
            Map.entry("id",            b.getId()),
            Map.entry("bookingRef",    b.getBookingRef()),
            Map.entry("status",        b.getStatus()),
            Map.entry("travelDate",    b.getTravelDate().toString()),
            Map.entry("seatNumbers",   b.getSeatNumbers()),
            Map.entry("passengerName", b.getPassengerName()),
            Map.entry("passengerPhone",b.getPassengerPhone()),
            Map.entry("passengerAge",  b.getPassengerAge()),
            Map.entry("totalAmount",   b.getTotalAmount()),
            Map.entry("paymentMethod", b.getPaymentMethod() == null ? "N/A" : b.getPaymentMethod()),
            Map.entry("paymentReference", b.getPaymentReference() == null ? "N/A" : b.getPaymentReference()),
            Map.entry("paymentStatus", b.getPaymentStatus() == null ? "N/A" : b.getPaymentStatus()),
            Map.entry("bus", Map.of(
                "id",            bus.getId(),
                "operatorName",  bus.getOperatorName(),
                "busType",       bus.getBusType(),
                "fromCity",      bus.getFromCity(),
                "toCity",        bus.getToCity(),
                "departureTime", bus.getDepartureTime(),
                "arrivalTime",   bus.getArrivalTime(),
                "pricePerSeat",  bus.getPricePerSeat()
            ))
        );
    }
}

