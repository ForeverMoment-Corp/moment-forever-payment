package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.commons.EventConstants;
import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.events.PaymentRequestedEvent;
import com.stripe.Stripe;
import com.stripe.net.RequestOptions;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Service dedicated to interacting with the Stripe API.
 * Responsible solely for translating our domain models into Stripe requests and capturing the response.
 */
@Service
public class StripePaymentProcessor implements PaymentStrategy {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProcessor.class);

    @Value("${payment.gateway.api-key:}")
    private String stripeApiKey;

    @Value("${payment.gateway.success-url:http://localhost:8082/payment/success?session_id={CHECKOUT_SESSION_ID}}")
    private String successUrl;

    @Value("${payment.gateway.cancel-url:http://localhost:8082/payment/cancel}")
    private String cancelUrl;

    @Override
    public Payment initiatePayment(InboundPaymentEvent inboundPaymentEvent) {
        if (stripeApiKey.isBlank()) {
            throw new IllegalStateException("PAYMENT_GATEWAY_API_KEY must be configured");
        }
        Stripe.apiKey = stripeApiKey;
        
        Payment payment = new Payment();
        payment.setProvider("STRIPE");
        payment.setBookingId(inboundPaymentEvent.getBookingId());
        payment.setCorrelationId(inboundPaymentEvent.getCorrelationId());
        payment.setStatus(PaymentStatus.PENDING);
        
        if (inboundPaymentEvent instanceof PaymentRequestedEvent) {
            PaymentRequestedEvent requestedEvent = (PaymentRequestedEvent) inboundPaymentEvent;
            payment.setAmount(requestedEvent.getGrandTotal());
            // TODO: hardcoded for now, later will be removed
            payment.setAmount(BigDecimal.valueOf(51000.1D));
            payment.setCurrency(requestedEvent.getCurrency());
        }

        try {
            // Build the line item for Stripe Checkout
            SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(payment.getCurrency() != null ? payment.getCurrency().toLowerCase() : "usd")
                                    .setUnitAmount(payment.getAmount() != null ? payment.getAmount().multiply(new BigDecimal(100)).longValue() : 5000L)
                                    .setProductData(
                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                    .setName("Booking " + payment.getBookingId())
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .addLineItem(lineItem)
                    .setClientReferenceId(payment.getBookingId())
                    .build();

            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey(inboundPaymentEvent.getEventId())
                    .build();
            Session session = Session.create(params, requestOptions);
            payment.setProviderSessionId(session.getId());
            payment.setPaymentUrl(session.getUrl());
            log.info("Created Stripe Checkout Session: {} for booking: {}",
                    session.getId(), payment.getBookingId());

        } catch (Exception e) {
            log.error("Failed to create Stripe Checkout Session", e);
            throw new IllegalStateException("Stripe Checkout Session creation failed", e);
        }

        return payment;
    }

    @Override
    public String getSupportedPaymentType() {
        return "STRIPE";
    }
}