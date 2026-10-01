package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.commons.EventConstants;
import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.events.OutGoingEvent;
import com.forvmom.MomentForeverPayment.events.PaymentInitiatedEvent;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.time.Instant;

/**
 * Factory for creating a PaymentInitiatedEvent.
 * Purely responsible for mapping the domain state to the event payload.
 */
@Service
public class PaymentInitiatedEventFactory implements OutgoingEventFactory {

    @Override
    public OutGoingEvent createEvent(InboundPaymentEvent inboundPaymentEvent, Payment payment) {
        PaymentInitiatedEvent event = new PaymentInitiatedEvent();
        event.setBookingId(inboundPaymentEvent.getBookingId());
        event.setPaymentUrl(payment.getPaymentUrl());
        event.setProviderSessionId(payment.getProviderSessionId());
        event.setProvider(payment.getProvider());
        event.setEventId(UUID.randomUUID().toString());
        event.setProducer("payment-service");
        event.setCorrelationId(inboundPaymentEvent.getCorrelationId());
        event.setCausationId(inboundPaymentEvent.getEventId());
        event.setSchemaVersion(1);
        event.setOccurredAt(Instant.now());
        
        return event;
    }

    @Override
    public String getSupportedEventType() {
        return EventConstants.PAYMENT_INITIATED;
    }
}