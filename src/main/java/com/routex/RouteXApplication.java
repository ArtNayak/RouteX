package com.routex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RouteX — Online Bus Booking System
 * Entry point for the Spring Boot application.
 *
 * Architecture (IEEE SRS §2.1):
 *   Presentation Layer  → HTML/CSS/JS (src/main/resources/static/)
 *   Business Logic Layer→ Service classes (com.routex.service)
 *   Data Access Layer   → JPA Repositories (com.routex.repository)
 */
@SpringBootApplication
public class RouteXApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouteXApplication.class, args);
    }
}
