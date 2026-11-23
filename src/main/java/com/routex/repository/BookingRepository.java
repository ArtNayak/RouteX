package com.routex.repository;

import com.routex.model.Booking;
import com.routex.model.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access layer for Booking entity.
 * Supports §4.2 Seat Management and §4.3 Bill Generation.
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingRef(String bookingRef);

    /** All bookings for a specific user (My Bookings page) */
    List<Booking> findByUserIdOrderByBookedAtDesc(Long userId);

    /**
     * Confirmed bookings for a bus on a travel date —
     * used to determine which seats are taken (REQ-4.2.2, REQ-4.1.3).
     */
    List<Booking> findByBusAndTravelDateAndStatus(Bus bus, LocalDate travelDate, String status);

    /** Used by admin bus management to safely block deletion of buses that have bookings */
    boolean existsByBus(Bus bus);

    /** Admin: all bookings ordered by most recent first */
    List<Booking> findAllByOrderByBookedAtDesc();

    /** Admin list with eager bus/user to avoid lazy proxy serialization issues */
    @Query("SELECT b FROM Booking b JOIN FETCH b.bus JOIN FETCH b.user ORDER BY b.bookedAt DESC")
    List<Booking> findAllForAdmin();

    /** Revenue total for admin dashboard */
    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.status = 'CONFIRMED'")
    Double getTotalRevenue();

    /** Count confirmed bookings */
    long countByStatus(String status);
}
