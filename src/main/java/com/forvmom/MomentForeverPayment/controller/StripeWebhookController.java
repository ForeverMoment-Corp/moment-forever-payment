package com.forvmom.MomentForeverPayment.controller;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.scheduler.OutgoingPaymentPublisher;
import com.forvmom.MomentForeverPayment.service.WebhookProcessingService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller specifically for handling incoming Webhooks from Stripe.
 */
@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    @Value("${payment.gateway.webhook-secret:}")
    private String endpointSecret;

    private final WebhookProcessingService webhookProcessingService;
    private final OutgoingPaymentPublisher outgoingPaymentPublisher;

    public StripeWebhookController(WebhookProcessingService webhookProcessingService,
                                   OutgoingPaymentPublisher outgoingPaymentPublisher) {
        this.webhookProcessingService = webhookProcessingService;
        this.outgoingPaymentPublisher = outgoingPaymentPublisher;
    }

    @PostMapping
    @Operation(summary = "Stripe webhook callback", security = {})
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
            if (endpointSecret.isBlank()) {
                log.error("Stripe webhook secret is not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("Webhook endpoint is not configured");
            }
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (Exception e) {
            log.error("Webhook signature verification failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Signature Verification Failed");
        }

        OutgoingPaymentOutbox outgoingRecord = null;

        // Step 2: Process known event types
        switch (event.getType()) {
            case "checkout.session.completed":
            case "checkout.session.async_payment_succeeded":
            case "payment_intent.succeeded":
                outgoingRecord = handleCheckoutSessionCompleted(event);
                break;
            case "checkout.session.expired":
            case "checkout.session.async_payment_failed":
            case "payment_intent.payment_failed":
                outgoingRecord = handleCheckoutSessionExpired(event);
                break;
            default:
                log.info("Unhandled event type: {}", event.getType());
        }

        // Step 3: Trigger async publisher if an outbox record was created
        if (outgoingRecord != null) {
            outgoingPaymentPublisher.trySinglePublish(outgoingRecord);
        }

        // Return a 200 OK so Stripe knows we received the webhook successfully
        return ResponseEntity.ok("Success");
    }

    private OutgoingPaymentOutbox handleCheckoutSessionCompleted(Event event) {
        Session session = deserializeSession(event);
        if (!"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            log.info("Checkout session {} is not paid yet; waiting for an asynchronous result",
                    session.getId());
            return null;
        }
        log.info("Processing completed checkout session for Stripe Session ID: {}", session.getId());
        return webhookProcessingService.processWebhookAtomically(
                "STRIPE",
                event.getId(),
                session.getId(),
                session.getPaymentIntent(),
                PaymentStatus.SUCCESS
        );
    }
    
    private OutgoingPaymentOutbox handleCheckoutSessionExpired(Event event) {
        Session session = deserializeSession(event);
        log.info("Processing failed checkout session for Stripe Session ID: {}", session.getId());
        return webhookProcessingService.processWebhookAtomically(
                "STRIPE",
                event.getId(),
                session.getId(),
                session.getPaymentIntent(),
                PaymentStatus.FAILED
        );
    }
    private Session deserializeSession(Event event) {

        EventDataObjectDeserializer deserializer =
                event.getDataObjectDeserializer();

        log.info("Stripe Event ID: {}", event.getId());
        log.info("Stripe Event Type: {}", event.getType());
        log.info("Stripe API Version: {}", event.getApiVersion());

        log.info("Raw event data: {}", deserializer.getRawJson());

        if (deserializer.getObject().isPresent()) {

            Object stripeObject = deserializer.getObject().get();

            log.info("Deserialized object class: {}",
                    stripeObject.getClass().getName());

            log.info("Deserialized object: {}", stripeObject);

            if (stripeObject instanceof Session session) {
                return session;
            }

            log.error(
                    "Expected Session but got {}",
                    stripeObject.getClass().getName()
            );

        } else {
            log.error("Stripe SDK could not deserialize event data object");
        }

        throw new IllegalArgumentException(
                "Stripe event " + event.getId()
                        + " did not contain a compatible checkout session"
        );
    }
}
