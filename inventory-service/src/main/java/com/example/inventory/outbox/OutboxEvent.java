package com.example.inventory.outbox;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "outbox")
@Data
@NoArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String topic;
    private String messageKey;
    private String payload;      // JSON
    private String status;       // PENDING | SENT
    private int attempts;
    private Instant createdAt;
    private Instant sentAt;
}
