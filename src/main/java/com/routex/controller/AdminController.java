package com.routex.controller;

import com.routex.model.Booking;
import com.routex.model.Bus;
import com.routex.service.BookingService;
import com.routex.service.BusService;
import com.routex.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Presentation Layer — Admin Dashboard endpoints.
 * All routes require ROLE_ADMIN (§2.3.2, §5.5).
 *
 * GET  /api/admin/stats     — bookings count, revenue, user count
 * GET  /api/admin/bookings  — all bookings
 * GET  /api/admin/users     — all users
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final BookingService bookingService;
    private final BusService     busService;
    private final UserService    userService;

    @Data
    public static class BusManageRequest {
        @NotBlank private String busNumber;
        @NotBlank private String operatorName;
        @NotBlank private String busType;
        @NotBlank private String fromCity;
        @NotBlank private String toCity;
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Use HH:mm format")
        private String departureTime;
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Use HH:mm format")
        private String arrivalTime;
        @NotBlank private String duration;
        @NotNull @Positive private Double pricePerSeat;
        @NotNull @Min(1) private Integer totalSeats;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
            "totalBookings",    bookingService.getTotalBookings(),
            "confirmedBookings",bookingService.getConfirmedCount(),
            "totalRevenue",     bookingService.getTotalRevenue(),
            "totalBuses",       busService.getAllBuses().size(),
            "totalUsers",       userService.getAllUsers().size()
        ));
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> allBookings() {
        return ResponseEntity.ok(
            bookingService.getAllBookings().stream()
                .map(this::bookingToMap)
                .toList()
        );
    }

    @GetMapping("/users")
    public ResponseEntity<?> allUsers() {
        return ResponseEntity.ok(
            userService.getAllUsers().stream()
                .map(u -> Map.of("id", u.getId(), "name", u.getName(),
                        "email", u.getEmail(), "role", u.getRole()))
                .toList()
        );
    }

    @GetMapping("/buses")
    public ResponseEntity<List<Bus>> allBuses() {
        return ResponseEntity.ok(busService.getAllBuses());
    }

    @PostMapping("/buses")
    public ResponseEntity<?> addBus(@Valid @RequestBody BusManageRequest req) {
        try {
            Bus created = busService.createBus(toBus(req));
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/buses/{id}")
    public ResponseEntity<?> updateBus(@PathVariable Long id, @Valid @RequestBody BusManageRequest req) {
        try {
            Bus updated = busService.updateBus(id, toBus(req));
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Bus not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/buses/{id}")
    public ResponseEntity<?> deleteBus(@PathVariable Long id) {
        try {
            busService.deleteBus(id);
            return ResponseEntity.ok(Map.of("message", "Bus deleted successfully"));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Bus not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Bus toBus(BusManageRequest req) {
        Bus bus = new Bus();
        bus.setBusNumber(req.getBusNumber());
        bus.setOperatorName(req.getOperatorName());
        bus.setBusType(req.getBusType());
        bus.setFromCity(req.getFromCity());
        bus.setToCity(req.getToCity());
        bus.setDepartureTime(req.getDepartureTime());
        bus.setArrivalTime(req.getArrivalTime());
        bus.setDuration(req.getDuration());
        bus.setPricePerSeat(req.getPricePerSeat());
        bus.setTotalSeats(req.getTotalSeats());
        return bus;
    }

    private Map<String, Object> bookingToMap(Booking b) {
        Bus bus = b.getBus();
        return Map.ofEntries(
            Map.entry("id", b.getId()),
            Map.entry("bookingRef", b.getBookingRef()),
            Map.entry("status", b.getStatus()),
            Map.entry("travelDate", b.getTravelDate().toString()),
            Map.entry("seatNumbers", b.getSeatNumbers()),
            Map.entry("passengerName", b.getPassengerName()),
            Map.entry("passengerPhone", b.getPassengerPhone()),
            Map.entry("passengerAge", b.getPassengerAge()),
            Map.entry("totalAmount", b.getTotalAmount()),
            Map.entry("paymentMethod", b.getPaymentMethod() == null ? "N/A" : b.getPaymentMethod()),
            Map.entry("paymentReference", b.getPaymentReference() == null ? "N/A" : b.getPaymentReference()),
            Map.entry("paymentStatus", b.getPaymentStatus() == null ? "N/A" : b.getPaymentStatus()),
            Map.entry("bookedAt", b.getBookedAt() == null ? null : b.getBookedAt().toString()),
            Map.entry("bus", Map.of(
                "id", bus.getId(),
                "operatorName", bus.getOperatorName(),
                "busType", bus.getBusType(),
                "fromCity", bus.getFromCity(),
                "toCity", bus.getToCity(),
                "departureTime", bus.getDepartureTime(),
                "arrivalTime", bus.getArrivalTime(),
                "pricePerSeat", bus.getPricePerSeat()
            ))
        );
    }
}
