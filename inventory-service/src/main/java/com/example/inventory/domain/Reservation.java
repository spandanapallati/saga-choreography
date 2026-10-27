package com.example.inventory.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservation")
@Data
@NoArgsConstructor
public class Reservation {

    @Id
    private String orderId;          // one reservation per order = natural idempotency key
    private Long productId;
    private int quantity;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
}
