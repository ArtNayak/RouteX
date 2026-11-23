package com.routex.service;

import com.routex.model.Booking;
import com.routex.model.Bus;
import com.routex.repository.BookingRepository;
import com.routex.repository.BusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Business Logic Layer — Bus and Seat operations.
 *
 * Implements requirements:
 *   §4.1 Bus Search and Seat Booking
 *   §4.2 Seat Management (REQ-4.2.1, REQ-4.2.2)
 */
@Service
@RequiredArgsConstructor
public class BusService {

    private final BusRepository busRepository;
    private final BookingRepository bookingRepository;

    // ----------------------------------------------------------------
    //  Search (REQ-4.1.1 / REQ-4.1.2)
    // ----------------------------------------------------------------

    public List<Bus> searchBuses(String from, String to) {
        return busRepository.findByFromCityIgnoreCaseAndToCityIgnoreCase(from, to);
    }

    public Optional<Bus> findById(Long id) {
        return busRepository.findById(id);
    }

    public List<Bus> getAllBuses() {
        return busRepository.findAll();
    }

    public Bus saveBus(Bus bus) {
        validateBus(bus);
        if (bus.getId() == null && busRepository.existsByBusNumber(bus.getBusNumber())) {
            throw new IllegalStateException("Bus number already exists: " + bus.getBusNumber());
        }
        return busRepository.save(bus);
    }

    @Transactional
    public Bus createBus(Bus bus) {
        validateBus(bus);
        if (busRepository.existsByBusNumber(bus.getBusNumber())) {
            throw new IllegalStateException("Bus number already exists: " + bus.getBusNumber());
        }
        return busRepository.save(bus);
    }

    @Transactional
    public Bus updateBus(Long busId, Bus updates) {
        validateBus(updates);

        Bus existing = busRepository.findById(busId)
                .orElseThrow(() -> new IllegalArgumentException("Bus not found: " + busId));

        String nextBusNumber = updates.getBusNumber().trim();
        if (!existing.getBusNumber().equalsIgnoreCase(nextBusNumber)
                && busRepository.existsByBusNumber(nextBusNumber)) {
            throw new IllegalStateException("Bus number already exists: " + nextBusNumber);
        }

        existing.setBusNumber(nextBusNumber);
        existing.setOperatorName(updates.getOperatorName().trim());
        existing.setBusType(updates.getBusType().trim());
        existing.setFromCity(updates.getFromCity().trim());
        existing.setToCity(updates.getToCity().trim());
        existing.setDepartureTime(normalizeTime(updates.getDepartureTime()));
        existing.setArrivalTime(normalizeTime(updates.getArrivalTime()));
        existing.setDuration(updates.getDuration().trim());
        existing.setPricePerSeat(updates.getPricePerSeat());
        existing.setTotalSeats(updates.getTotalSeats());

        return busRepository.save(existing);
    }

    @Transactional
    public void deleteBus(Long busId) {
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new IllegalArgumentException("Bus not found: " + busId));

        if (bookingRepository.existsByBus(bus)) {
            throw new IllegalStateException(
                    "Cannot delete bus because bookings exist for this bus. Cancel or archive bookings first.");
        }
        busRepository.delete(bus);
    }

    // ----------------------------------------------------------------
    //  Seat Management (§4.2)
    // ----------------------------------------------------------------

    /**
     * Returns all seat numbers that are currently booked for a given bus + date.
     * REQ-4.2.2: validate availability before booking.
     */
    public List<Integer> getBookedSeats(Long busId, LocalDate date) {
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new IllegalArgumentException("Bus not found: " + busId));

        List<Booking> activeBookings =
                bookingRepository.findByBusAndTravelDateAndStatus(bus, date, "CONFIRMED");

        return activeBookings.stream()
                .flatMap(b -> b.getSeatList().stream())
                .collect(Collectors.toList());
    }

    /**
     * Returns all seat numbers for a bus as a complete layout (1..totalSeats).
     * REQ-4.1.2: display available seats using graphical seat layout.
     */
    public List<Integer> getAllSeats(Long busId) {
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new IllegalArgumentException("Bus not found: " + busId));
        return IntStream.rangeClosed(1, bus.getTotalSeats())
                .boxed()
                .collect(Collectors.toList());
    }

    /**
     * Checks whether all requested seats are available for the given bus + date.
     * REQ-4.1.3: prevent booking seats that are already reserved.
     */
    public boolean areSeatsAvailable(Long busId, LocalDate date, List<Integer> requestedSeats) {
        List<Integer> booked = getBookedSeats(busId, date);
        return requestedSeats.stream().noneMatch(booked::contains);
    }

    /** A bus together with how many of its seats are still free on a given date. */
    public record BusAvailability(Bus bus, int availableSeats) {}

    /** Search results with accurate availability for the travel date (REQ-4.1.2). */
    public List<BusAvailability> searchWithAvailability(String from, String to, LocalDate date) {
        return searchBuses(from, to).stream()
                .map(bus -> new BusAvailability(bus,
                        bus.getTotalSeats() - getBookedSeats(bus.getId(), date).size()))
                .toList();
    }

    public List<String> getAvailableCities() {
        return busRepository.findAll().stream()
                .flatMap(b -> java.util.stream.Stream.of(b.getFromCity(), b.getToCity()))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private void validateBus(Bus bus) {
        if (bus == null) throw new IllegalArgumentException("Bus payload is required.");
        if (isBlank(bus.getBusNumber())) throw new IllegalArgumentException("Bus number is required.");
        if (isBlank(bus.getOperatorName())) throw new IllegalArgumentException("Operator name is required.");
        if (isBlank(bus.getBusType())) throw new IllegalArgumentException("Bus type is required.");
        if (isBlank(bus.getFromCity())) throw new IllegalArgumentException("From city is required.");
        if (isBlank(bus.getToCity())) throw new IllegalArgumentException("To city is required.");
        if (bus.getFromCity().trim().equalsIgnoreCase(bus.getToCity().trim())) {
            throw new IllegalArgumentException("From city and to city cannot be same.");
        }
        if (isBlank(bus.getDepartureTime())) throw new IllegalArgumentException("Departure time is required.");
        if (isBlank(bus.getArrivalTime())) throw new IllegalArgumentException("Arrival time is required.");
        if (isBlank(bus.getDuration())) throw new IllegalArgumentException("Duration is required.");
        if (bus.getPricePerSeat() == null || bus.getPricePerSeat() <= 0) {
            throw new IllegalArgumentException("Price per seat must be greater than 0.");
        }
        if (bus.getTotalSeats() == null || bus.getTotalSeats() < 1) {
            throw new IllegalArgumentException("Total seats must be at least 1.");
        }

        bus.setBusNumber(bus.getBusNumber().trim().toUpperCase(Locale.ROOT));
        bus.setOperatorName(bus.getOperatorName().trim());
        bus.setBusType(bus.getBusType().trim());
        bus.setFromCity(bus.getFromCity().trim());
        bus.setToCity(bus.getToCity().trim());
        bus.setDepartureTime(normalizeTime(bus.getDepartureTime()));
        bus.setArrivalTime(normalizeTime(bus.getArrivalTime()));
        bus.setDuration(bus.getDuration().trim());
    }

    private String normalizeTime(String time) {
        String value = time == null ? "" : time.trim();
        if (!value.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            throw new IllegalArgumentException("Time must be in HH:mm format.");
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
