package com.forvmom.MomentForeverPayment.events;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;

public class PaymentRequestedEvent implements InboundPaymentEvent {
    private String eventType = "PAYMENT_REQUESTED";
    private String bookingId;
    private Long userId;
    private String userEmail;
    private Long experienceId;
    private String experienceName;
    private Long timeSlotMapperId;
    private Integer guestCount;
    private BigDecimal grandTotal;
    private String currency;
    private LocalDateTime requestedAt;
    private String paymentType; // e.g., "STRIPE"
    private String eventId;
    private String producer;
    private String correlationId;
    private String causationId;
    private Integer schemaVersion = 1;
    private Instant occurredAt;

    @Override
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    @Override
    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public String getBookingId() {
        return bookingId;
    }

    @Override
    public String getPaymentType() {
        return paymentType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Long getExperienceId() {
        return experienceId;
    }

    public void setExperienceId(Long experienceId) {
        this.experienceId = experienceId;
    }

    public String getExperienceName() {
        return experienceName;
    }

    public void setExperienceName(String experienceName) {
        this.experienceName = experienceName;
    }

    public Long getTimeSlotMapperId() {
        return timeSlotMapperId;
    }

    public void setTimeSlotMapperId(Long timeSlotMapperId) {
        this.timeSlotMapperId = timeSlotMapperId;
    }

    public Integer getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(Integer guestCount) {
        this.guestCount = guestCount;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    @Override
    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    @Override
    public String getCausationId() {
        return causationId;
    }

    public void setCausationId(String causationId) {
        this.causationId = causationId;
    }

    @Override
    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
