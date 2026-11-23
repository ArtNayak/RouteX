package com.routex.repository;

import com.routex.model.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

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
}
