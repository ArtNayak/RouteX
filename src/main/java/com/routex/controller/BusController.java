package com.routex.controller;

import com.routex.model.Bus;
import com.routex.service.BusService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Presentation Layer — Bus REST endpoints.
 *
 * GET  /api/buses/cities              — list all cities (for dropdowns)
 * GET  /api/buses/search?from=&to=    — search buses by route (§4.1)
 * GET  /api/buses/{id}/seats?date=    — get booked seats for a bus+date (§4.2)
 * POST /api/buses    [ADMIN]          — add new bus
 */
@RestController
@RequestMapping("/api/buses")
@RequiredArgsConstructor
public class BusController {

    private final BusService busService;

    @GetMapping("/cities")
    public ResponseEntity<List<String>> getCities() {
        return ResponseEntity.ok(busService.getAvailableCities());
    }

    /** REQ-4.1.1, REQ-4.1.2: search buses by route */
    @GetMapping("/search")
    public ResponseEntity<?> searchBuses(
            @RequestParam String from,
            @RequestParam String to) {

        if (from.equalsIgnoreCase(to)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Source and destination cannot be the same."));
        }
        List<Bus> results = busService.searchBuses(from, to);
        return ResponseEntity.ok(results);
    }

    /**
     * REQ-4.2.1, REQ-4.2.2: return booked seat numbers for a bus on a date.
     * The frontend uses this to render the seat map.
     */
    @GetMapping("/{id}/seats")
    public ResponseEntity<?> getSeats(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<Integer> booked = busService.getBookedSeats(id, date);
        List<Integer> all    = busService.getAllSeats(id);
        return ResponseEntity.ok(Map.of("all", all, "booked", booked));
    }

    /** Admin: add new bus to the fleet */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Bus> addBus(@RequestBody Bus bus) {
        return ResponseEntity.ok(busService.saveBus(bus));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Bus>> getAllBuses() {
        return ResponseEntity.ok(busService.getAllBuses());
    }
}
