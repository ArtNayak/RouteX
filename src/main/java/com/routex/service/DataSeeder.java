package com.routex.service;

import com.routex.model.Bus;
import com.routex.model.User;
import com.routex.repository.BusRepository;
import com.routex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the database with initial buses and an admin account.
 * Runs once on startup if data is absent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final BusRepository busRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedAdmin();
        seedBuses();
        log.info("RouteX DataSeeder complete — {} buses loaded.", busRepository.count());
    }

    private void seedAdmin() {
        if (userRepository.existsByEmail("admin@routex.com")) return;
        userRepository.save(User.builder()
                .name("Admin User")
                .email("admin@routex.com")
                .password(passwordEncoder.encode("admin123"))
                .role("ADMIN")
                .build());
        log.info("Admin seeded: admin@routex.com / admin123");
    }

    private void seedBuses() {
        if (busRepository.count() > 0) return;

        List<Bus> buses = List.of(
            bus("MH01", "Shivneri",      "AC Sleeper",           "Mumbai",     "Pune",       "06:00","09:30","3h 30m", 350.0, 40),
            bus("MH02", "Parivartan",    "Non-AC Seater",        "Mumbai",     "Pune",       "08:30","12:00","3h 30m", 220.0, 40),
            bus("MH03", "OrangeTrails",  "Volvo Multi-Axle",     "Mumbai",     "Pune",       "14:00","17:00","3h 00m", 450.0, 36),
            bus("MH04", "MSRTC",         "Non-AC Seater",        "Pune",       "Nashik",     "07:00","10:30","3h 30m", 180.0, 44),
            bus("MH05", "ZipTravel",     "AC Seater",            "Pune",       "Nashik",     "10:00","13:00","3h 00m", 280.0, 40),
            bus("MH06", "Shivneri",      "AC Sleeper",           "Mumbai",     "Nashik",     "07:30","11:00","3h 30m", 320.0, 40),
            bus("MH07", "OrangeTrails",  "Volvo Multi-Axle",     "Mumbai",     "Nashik",     "15:00","18:30","3h 30m", 420.0, 36),
            bus("MH08", "MSRTC",         "Non-AC Seater",        "Pune",       "Aurangabad", "06:30","11:00","4h 30m", 250.0, 44),
            bus("MH09", "Parivartan",    "AC Seater",            "Pune",       "Aurangabad", "09:00","13:00","4h 00m", 360.0, 40),
            bus("MH10", "ZipTravel",     "AC Sleeper",           "Mumbai",     "Nagpur",     "18:00","06:00","12h 00m",650.0, 36),
            bus("MH11", "Shivneri",      "Volvo Multi-Axle",     "Mumbai",     "Nagpur",     "20:00","08:00","12h 00m",750.0, 40),
            bus("MH12", "MSRTC",         "Non-AC Seater",        "Nashik",     "Shirdi",     "08:00","10:00","2h 00m", 120.0, 44),
            bus("MH13", "OrangeTrails",  "AC Seater",            "Nashik",     "Shirdi",     "11:00","13:00","2h 00m", 200.0, 40),
            bus("MH14", "Parivartan",    "Non-AC Seater",        "Pune",       "Kolhapur",   "07:00","11:30","4h 30m", 220.0, 44),
            bus("MH15", "ZipTravel",     "AC Seater",            "Pune",       "Kolhapur",   "13:00","17:00","4h 00m", 320.0, 40),
            bus("MH16", "MSRTC",         "Non-AC Seater",        "Mumbai",     "Solapur",    "07:00","13:00","6h 00m", 300.0, 44),
            bus("MH17", "Shivneri",      "AC Sleeper",           "Mumbai",     "Solapur",    "21:00","03:00","6h 00m", 480.0, 36),
            bus("MH18", "OrangeTrails",  "AC Seater",            "Aurangabad", "Nagpur",     "06:00","12:00","6h 00m", 380.0, 40),
            bus("MH19", "Parivartan",    "Non-AC Seater",        "Nagpur",     "Pune",       "08:00","18:00","10h 00m",400.0, 44),
            bus("MH20", "ZipTravel",     "Volvo Multi-Axle",     "Kolhapur",   "Mumbai",     "22:00","06:00","8h 00m", 550.0, 36)
        );
        busRepository.saveAll(buses);
    }

    private Bus bus(String num, String op, String type, String from, String to,
                    String dep, String arr, String dur, double price, int seats) {
        return Bus.builder()
                .busNumber(num).operatorName(op).busType(type)
                .fromCity(from).toCity(to)
                .departureTime(dep).arrivalTime(arr).duration(dur)
                .pricePerSeat(price).totalSeats(seats)
                .build();
    }
}
