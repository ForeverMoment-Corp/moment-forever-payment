package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.commons.EventConstants;
import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.events.OutGoingEvent;
import com.forvmom.MomentForeverPayment.events.PaymentFailedEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.time.Instant;

/**
 * Factory for creating a PaymentFailedEvent.
 * Purely responsible for mapping the domain state to the event payload.
 */
@Service
public class PaymentFailedEventFactory implements OutgoingEventFactory {

    @Override
    public OutGoingEvent createEvent(InboundPaymentEvent inboundPaymentEvent, Payment paymentResult) {
        PaymentFailedEvent paymentFailedEvent=new PaymentFailedEvent();
        paymentFailedEvent.setBookingId(inboundPaymentEvent.getBookingId());
        paymentFailedEvent.setEventId(UUID.randomUUID().toString());
        paymentFailedEvent.setProducer("payment-service");
        paymentFailedEvent.setCorrelationId(inboundPaymentEvent.getCorrelationId());
        paymentFailedEvent.setCausationId(inboundPaymentEvent.getEventId());
        paymentFailedEvent.setSchemaVersion(1);
        paymentFailedEvent.setOccurredAt(Instant.now());
        paymentFailedEvent.setFailureReason("Payment processing failed");
        paymentFailedEvent.setErrorCode("PAYMENT_FAILED");
        paymentFailedEvent.setFailedAt(LocalDateTime.now());
        paymentFailedEvent.setAttemptedAmount(paymentResult.getAmount());
        paymentFailedEvent.setCurrency(paymentResult.getCurrency());
        return paymentFailedEvent;
    }

    @Override
    public String getSupportedEventType() {
        return EventConstants.PAYMENT_FAILED;
    }
}
