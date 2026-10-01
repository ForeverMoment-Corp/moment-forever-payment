package com.forvmom.MomentForeverPayment.events;

import java.time.Instant;

public interface InboundPaymentEvent {
    String getEventType();
    String getBookingId();
    String getPaymentType();
    String getEventId();
    String getProducer();

    default String getCorrelationId() {
        return null;
    }

    default String getCausationId() {
        return null;
    }

    default Integer getSchemaVersion() {
        return 1;
    }

    default Instant getOccurredAt() {
        return null;
    }
}