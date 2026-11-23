package com.routex.repository;

import com.routex.model.Bus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for Bus entity.
 * Supports §4.1 Bus Search (REQ-4.1.1, REQ-4.1.2).
 */
@Repository
public interface BusRepository extends JpaRepository<Bus, Long> {

    /** Find all buses for a given route — core search (§4.1) */
    List<Bus> findByFromCityIgnoreCaseAndToCityIgnoreCase(String fromCity, String toCity);

    /** Find all buses from a city */
    List<Bus> findByFromCityIgnoreCase(String fromCity);

    boolean existsByBusNumber(String busNumber);

    /**
     * Loads a bus and locks its row until the transaction ends. Booking takes this lock so that two
     * people booking the same bus at the same moment are processed one after the other, which makes
     * the "is this seat still free?" check and the insert safe (no double booking).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bus b WHERE b.id = :id")
    Optional<Bus> findByIdForUpdate(@Param("id") Long id);
}
