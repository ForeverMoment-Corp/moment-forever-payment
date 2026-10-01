package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;

/**
 * Interface for connecting to external payment gateways (e.g., Stripe, PayPal).
 * Follows the Single Responsibility Principle by only containing logic for external API calls.
 */
public interface PaymentStrategy {
    
    /**
     * Initiates a payment session with the provider.
     * 
     * @param inboundPaymentEvent The incoming request data from Kafka.
     * @return Payment entity populated with the provider's session ID and payment URL.
     */
    Payment initiatePayment(InboundPaymentEvent inboundPaymentEvent);
    
    /**
     * Declares the payment provider this strategy supports.
     * 
     * @return Provider identifier (e.g., "STRIPE").
     */
    String getSupportedPaymentType();
}
