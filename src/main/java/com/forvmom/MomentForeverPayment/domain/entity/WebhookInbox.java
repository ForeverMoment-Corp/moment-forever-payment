package com.forvmom.MomentForeverPayment.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity to track processed webhooks to ensure idempotency.
 * Prevents the same Stripe/Payment gateway webhook from being processed multiple times.
 */
@Entity
@Data
@Table(name = "webhook_inbox")
public class WebhookInbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "status")
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
