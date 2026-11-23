package com.routex.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Booking entity — covers §4.1 Bus Search & Seat Booking and §4.3 Bill Generation.
 *
 * REQ-4.1.4: stores customer name, seat numbers, price, and date.
 */
@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Auto-generated readable booking ID, e.g. "BK-20260120-0001" */
    @Column(nullable = false, unique = true)
    private String bookingRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    @NotNull
    @Column(nullable = false)
    private LocalDate travelDate;

    /** Comma-separated seat numbers, e.g. "3,4,15" */
    @NotBlank
    @Column(nullable = false)
    private String seatNumbers;

    @NotBlank
    @Column(nullable = false)
    private String passengerName;

    @NotBlank
    @Column(nullable = false)
    private String passengerPhone;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer passengerAge;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Double totalAmount;

    /** CARD / UPI / NET_BANKING / WALLET */
    @Column(nullable = false)
    @Builder.Default
    private String paymentMethod = "CARD";

    /** Transaction / payment reference from simulated payment step */
    @Column(nullable = false)
    @Builder.Default
    private String paymentReference = "N/A";

    /** PENDING / PAID / FAILED */
    @Column(nullable = false)
    @Builder.Default
    private String paymentStatus = "PAID";

    /** CONFIRMED / CANCELLED */
    @Column(nullable = false)
    @Builder.Default
    private String status = "CONFIRMED";

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime bookedAt = LocalDateTime.now();

    /** Helper: parse seatNumbers string to list */
    @Transient
    public List<Integer> getSeatList() {
        if (seatNumbers == null || seatNumbers.isBlank()) return List.of();
        return java.util.Arrays.stream(seatNumbers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }
}
