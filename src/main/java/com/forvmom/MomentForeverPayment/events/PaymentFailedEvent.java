package com.forvmom.MomentForeverPayment.events;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;

@Data
public class PaymentFailedEvent implements OutGoingEvent {
    private String eventType = "PAYMENT_FAILED";
    private String bookingId;
    private String failureReason;
    private String errorCode;
    private LocalDateTime failedAt;
    private BigDecimal attemptedAmount;
    private String currency;

    // Inventory fields needed for compensation
    private String experienceId;
    private String timeSlotMapperId;
    private Integer guestCount;
    private Long userId;
    private String userEmail;
    
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
//
//    @Override
//    public String getEventType() {
//        return eventType;
//    }


    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public BigDecimal getAttemptedAmount() {
        return attemptedAmount;
    }

    public void setAttemptedAmount(BigDecimal attemptedAmount) {
        this.attemptedAmount = attemptedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getExperienceId() {
        return experienceId;
    }

    public void setExperienceId(String experienceId) {
        this.experienceId = experienceId;
    }

    public String getTimeSlotMapperId() {
        return timeSlotMapperId;
    }

    public void setTimeSlotMapperId(String timeSlotMapperId) {
        this.timeSlotMapperId = timeSlotMapperId;
    }

    public Integer getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(Integer guestCount) {
        this.guestCount = guestCount;
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
}