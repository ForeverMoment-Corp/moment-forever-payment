package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.commons.EventConstants;
import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.events.OutGoingEvent;
import com.forvmom.MomentForeverPayment.events.PaymentProcessedEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.time.Instant;

/**
 * Factory for creating a PaymentProcessedEvent.
 * Purely responsible for mapping the domain state to the event payload.
 */
@Service
public class PaymentProcessedEventFactory implements OutgoingEventFactory {

    @Override
    public OutGoingEvent createEvent(InboundPaymentEvent inboundPaymentEvent, Payment paymentResult) {
        PaymentProcessedEvent paymentProcessedEvent=new PaymentProcessedEvent();
        paymentProcessedEvent.setBookingId(inboundPaymentEvent.getBookingId());
        paymentProcessedEvent.setTransactionId(paymentResult.getTransactionId());
        paymentProcessedEvent.setPaidAt(LocalDateTime.now());
        paymentProcessedEvent.setAmountPaid(paymentResult.getAmount());
        paymentProcessedEvent.setCurrency(paymentResult.getCurrency());
        paymentProcessedEvent.setPaymentMethod(paymentResult.getProvider());
        paymentProcessedEvent.setEventId(UUID.randomUUID().toString());
        paymentProcessedEvent.setProducer("payment-service");
        paymentProcessedEvent.setCorrelationId(inboundPaymentEvent.getCorrelationId());
        paymentProcessedEvent.setCausationId(inboundPaymentEvent.getEventId());
        paymentProcessedEvent.setSchemaVersion(1);
        paymentProcessedEvent.setOccurredAt(Instant.now());
        return paymentProcessedEvent;
    }

    @Override
    public String getSupportedEventType() {
        return EventConstants.PAYMENT_PROCESSED;
    }
}
