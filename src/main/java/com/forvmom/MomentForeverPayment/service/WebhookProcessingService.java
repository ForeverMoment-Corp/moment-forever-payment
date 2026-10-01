package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.domain.entity.WebhookInbox;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.events.OutGoingEvent;
import com.forvmom.MomentForeverPayment.repository.PaymentRepository;
import com.forvmom.MomentForeverPayment.repository.WebhookInboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service to handle incoming webhooks from external payment providers securely and idempotently.
 * Strictly maintains the database transaction boundary.
 */
@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final WebhookInboxRepository webhookInboxRepository;
    private final PaymentRepository paymentRepository;
    private final OutgoingEventFactoryRegistry outgoingEventFactoryRegistry;
    private final OutgoingPaymentOutboxService outgoingPaymentOutboxService;

    public WebhookProcessingService(
            WebhookInboxRepository webhookInboxRepository,
            PaymentRepository paymentRepository,
            OutgoingEventFactoryRegistry outgoingEventFactoryRegistry,
            OutgoingPaymentOutboxService outgoingPaymentOutboxService) {
        this.webhookInboxRepository = webhookInboxRepository;
        this.paymentRepository = paymentRepository;
        this.outgoingEventFactoryRegistry = outgoingEventFactoryRegistry;
        this.outgoingPaymentOutboxService = outgoingPaymentOutboxService;
    }

    /**
     * Atomically processes a webhook event, ensuring it's not a duplicate, updating the payment,
     * and scheduling the downstream notification.
     * 
     * @param provider The payment provider (e.g., "STRIPE")
     * @param webhookEventId The unique ID of the webhook event
     * @param providerSessionId The session ID the provider uses to identify the checkout
     * @param newStatus The new status of the payment
     * @return The created OutgoingPaymentOutbox record if successful, or null if duplicate/ignored.
     */
    @Transactional
    public OutgoingPaymentOutbox processWebhookAtomically(
            String provider,
            String webhookEventId,
            String providerSessionId,
            String transactionId,
            PaymentStatus newStatus) {
        // Step 1: Idempotency Check
        if (webhookInboxRepository.existsByEventId(webhookEventId)) {
            log.info("Webhook event {} already processed. Ignoring.", webhookEventId);
            return null;
        }

        // Step 2: Record Webhook to prevent future duplicates
        WebhookInbox inbox = new WebhookInbox();
        inbox.setEventId(webhookEventId);
        inbox.setProvider(provider);
        inbox.setStatus("PROCESSED");
        webhookInboxRepository.save(inbox);

        // Step 3: Find corresponding Payment
        Optional<Payment> paymentOpt =
                paymentRepository.findByProviderSessionIdForUpdate(providerSessionId);
        if (paymentOpt.isEmpty()) {
            log.error("Received webhook for unknown providerSessionId: {}", providerSessionId);
            throw new IllegalArgumentException("Unknown providerSessionId: " + providerSessionId);
        }

        Payment payment = paymentOpt.get();
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Payment for booking {} already in terminal state {}. Ignoring webhook update.", payment.getBookingId(), payment.getStatus());
            return null; // Already terminal
        }

        // Step 4: Update Payment Status
        payment.setStatus(newStatus);
        if (transactionId != null && !transactionId.isBlank()) {
            payment.setTransactionId(transactionId);
        }
        paymentRepository.save(payment);

        log.info("Payment for booking {} updated to {}", payment.getBookingId(), newStatus);

        // Step 5: Map Outgoing Event (PaymentProcessedEvent or PaymentFailedEvent)
        OutgoingEventFactory factory = outgoingEventFactoryRegistry.getFactoryForStatus(payment.getStatus());
        
        // Construct a synthetic inbound event to satisfy the factory signature
        // In a real scenario, you might adjust the factory interface to not require the InboundPaymentEvent if unavailable
        InboundPaymentEvent syntheticInbound = new InboundPaymentEvent() {
            @Override public String getEventType() { return "WEBHOOK_UPDATE"; }
            @Override public String getBookingId() { return payment.getBookingId(); }
            @Override public String getPaymentType() { return provider; }
            @Override public String getEventId() { return webhookEventId; }
            @Override public String getProducer() { return provider; }
            @Override public String getCorrelationId() { return payment.getCorrelationId(); }
        };

        OutGoingEvent outGoingEvent = factory.createEvent(syntheticInbound, payment);

        // Step 6: Create Outbox Record
        return outgoingPaymentOutboxService.createRecord(
                payment.getBookingId(),
                outGoingEvent.getEventType(),
                outGoingEvent.getProducer(),
                outGoingEvent.getEventId(),
                outGoingEvent
        );
    }
}
