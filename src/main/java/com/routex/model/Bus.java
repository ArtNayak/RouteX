package com.routex.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Bus entity — represents a bus route/service.
 * Covers §4.1 Bus Search and §4.2 Seat Management.
 */
@Entity
@Table(name = "buses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String busNumber;          // e.g. "MH-12-AB-1234"

    @NotBlank
    @Column(nullable = false)
    private String operatorName;       // e.g. "Shivneri"

    @NotBlank
    @Column(nullable = false)
    private String busType;            // AC Sleeper / Non-AC Seater / Volvo

    @NotBlank
    @Column(nullable = false)
    private String fromCity;

    @NotBlank
    @Column(nullable = false)
    private String toCity;

    @NotBlank
    @Column(nullable = false)
    private String departureTime;      // HH:mm

    @NotBlank
    @Column(nullable = false)
    private String arrivalTime;

    @NotBlank
    @Column(nullable = false)
    private String duration;           // e.g. "3h 30m"

    @NotNull
    @Positive
    @Column(nullable = false)
    private Double pricePerSeat;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer totalSeats;
}
