package com.forvmom.MomentForeverPayment.events;

import lombok.Data;

import java.time.Instant;

@Data
public class PaymentInitiatedEvent implements OutGoingEvent {
    private String eventType = "PAYMENT_INITIATED";
    private String bookingId;
    private String paymentUrl;
    private String providerSessionId;
    private String provider;
    private String eventId;
    private String producer;
    private String correlationId;
    private String causationId;
    private Integer schemaVersion = 1;
    private Instant occurredAt;

    @Override
    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    @Override
    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public String getProducer() {
        return producer;
    }
}