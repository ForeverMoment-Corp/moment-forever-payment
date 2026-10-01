package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentOutbox;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.scheduler.OutgoingPaymentPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Orchestrator service for handling inbound payment request events from Kafka.
 * It manages the strict transactional boundary across idempotency, payment creation, 
 * and transactional outbox persistence.
 */
@Service
public class PaymentProcessService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessService.class);

    private final PaymentOutboxService outboxService;
    private final PaymentProcessorRegistry paymentProcessorRegistry;
    private final PaymentTransactionService paymentTransactionService;
    private final OutgoingPaymentPublisher outgoingPaymentPublisher;

    public PaymentProcessService(
            PaymentOutboxService outboxService,
            PaymentProcessorRegistry paymentProcessorRegistry,
            PaymentTransactionService paymentTransactionService,
            OutgoingPaymentPublisher outgoingPaymentPublisher) {
        this.outboxService = outboxService;
        this.paymentProcessorRegistry = paymentProcessorRegistry;
        this.paymentTransactionService = paymentTransactionService;
        this.outgoingPaymentPublisher = outgoingPaymentPublisher;
    }

    /**
     * Entry point for incoming PaymentRequestedEvents.
     * Manages Kafka acknowledgement and delegates to the strict database transaction method.
     *
     * @param inboundPaymentEvent The received event payload.
     * @param ack Kafka acknowledgment object.
     */
    public void handleIncomingPaymentRequest(InboundPaymentEvent inboundPaymentEvent, Acknowledgment ack) {
        String bookingId = inboundPaymentEvent.getBookingId();
        log.info("Received payment request for bookingId={}", bookingId);

        // Step 1: Idempotency check (No transaction needed for this read/create-if-missing)
        PaymentOutbox outbox = outboxService.findOrCreateForEvent(inboundPaymentEvent);

        if (PaymentOutbox.STATUS_PROCESSED.equals(outbox.getStatus())) {
            log.info("Duplicate payment request ignored (already PROCESSED): bookingId={}", bookingId);
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }

        // Step 2: Mark as PROCESSING in an isolated transaction so Quartz sees the lock
        outbox = outboxService.markAsProcessing(outbox);
        
        // Step 3: Early ack to Kafka - we own it in the database now via the Inbox record
        if (ack != null) {
            ack.acknowledge();
        }

        // Step 4: Persist the payment, outgoing event, and inbox completion atomically.
        OutgoingPaymentOutbox outgoingPaymentOutbox = executePaymentTransaction(outbox, inboundPaymentEvent);
        
        // Step 5: Publish to Kafka (outside of database transaction to prevent locking DB during network calls)
        if (outgoingPaymentOutbox != null) {
            outgoingPaymentPublisher.trySinglePublish(outgoingPaymentOutbox);
        }
    }

    /**
     * The core transactional boundary for processing a payment request.
     * Marks inbox as processing, initiates payment via the gateway, saves the state, 
     * maps the outgoing event, and saves it to the outbox.
     */
    public OutgoingPaymentOutbox executePaymentTransaction(PaymentOutbox outbox, InboundPaymentEvent inboundPaymentEvent) {
        PaymentStrategy paymentStrategy = paymentProcessorRegistry.getPaymentTypeProcessor(inboundPaymentEvent.getPaymentType());
        Payment paymentResult = paymentStrategy.initiatePayment(inboundPaymentEvent);
        return paymentTransactionService.persistPaymentResult(outbox, inboundPaymentEvent, paymentResult);
    }

}
