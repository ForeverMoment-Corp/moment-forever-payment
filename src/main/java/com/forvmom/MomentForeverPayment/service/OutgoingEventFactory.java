package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.events.OutGoingEvent;

/**
 * Factory interface responsible solely for mapping an internal Payment state 
 * and its original Inbound Event to a specific Outgoing Event payload.
 */
public interface OutgoingEventFactory {
    
    /**
     * Constructs the outgoing event data without persisting it.
     * 
     * @param inboundPaymentEvent The original triggering event.
     * @param paymentResult The current state of the payment.
     * @return The outgoing event payload ready for outbox persistence.
     */
    OutGoingEvent createEvent(InboundPaymentEvent inboundPaymentEvent, Payment paymentResult);
    
    /**
     * Identifies which type of event this factory produces.
     */
    String getSupportedEventType();
}
