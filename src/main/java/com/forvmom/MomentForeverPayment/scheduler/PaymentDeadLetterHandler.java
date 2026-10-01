package com.forvmom.MomentForeverPayment.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forvmom.MomentForeverPayment.commons.EventConstants;
import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentOutbox;
import com.forvmom.MomentForeverPayment.events.PaymentFailedEvent;
import com.forvmom.MomentForeverPayment.events.PaymentProcessedEvent;
import com.forvmom.MomentForeverPayment.events.PaymentRequestedEvent;
import com.forvmom.MomentForeverPayment.service.AlertService;
import com.forvmom.MomentForeverPayment.service.OutgoingPaymentOutboxService;
import com.forvmom.MomentForeverPayment.service.PaymentOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentDeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentDeadLetterHandler.class);

    private final OutgoingPaymentOutboxService outgoingService;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;
    private final PaymentOutboxService paymentOutboxService;

    public PaymentDeadLetterHandler(OutgoingPaymentOutboxService outgoingService,
                                    PaymentOutboxService paymentOutboxService,
                                    AlertService alertService,
                                    ObjectMapper objectMapper) {
        this.outgoingService = outgoingService;
        this.alertService = alertService;
        this.objectMapper = objectMapper;
        this.paymentOutboxService=paymentOutboxService;
    }

    public void handleDeadRecordForOutgoingEvents(OutgoingPaymentOutbox record) {
        outgoingService.markAsDead(record);

        String eventType = record.getEventType();
        String bookingId = record.getBookingId();

        String msg = String.format(
                "🔴 PAYMENT DEAD LETTER: id=%d, type=%s, bookingId=%s, retries=%d",
                record.getId(), eventType, bookingId, record.getRetryCount()
        );

        log.error(msg);
        alertService.sendAlert(msg);

        // Compensation logic
        try {
            compensateDeadRecordForOutgoingEvents(record);
        } catch (Exception e) {
            log.error("Compensation failed for dead record id={}", record.getId(), e);
            alertService.sendAlert("Compensation failed for dead record " + record.getId());
        }
    }

    @Transactional
    public void handleDeadRecordForIncomingEvents(PaymentOutbox record) {
        try {
            compensateDeadRecordForIncomingEvents(record);
        } catch (Exception e) {
            log.error("Compensation failed for dead record id={}", record.getId(), e);
            alertService.sendAlert("Compensation failed for dead record " + record.getId());
            throw new IllegalStateException("Failed to create compensation for dead payment request", e);
        }

        paymentOutboxService.markAsDead(record);

        String msg = String.format(
                "🔴 PAYMENT DEAD LETTER: id=%d, type=%s, bookingId=%s, retries=%d",
                record.getId(), record.getEventType(), record.getBookingId(), record.getRetryCount()
        );
        log.error(msg);
        alertService.sendAlert(msg);
    }

    private void compensateDeadRecordForOutgoingEvents(OutgoingPaymentOutbox record) throws Exception {
        String eventType = record.getEventType();
        String payload = record.getPayload();

        switch (eventType) {
            case EventConstants.PAYMENT_PROCESSED:
                PaymentProcessedEvent processed = objectMapper.readValue(payload, PaymentProcessedEvent.class);
                log.warn("Dead PAYMENT_PROCESSED for booking {} - manual refund may be needed",
                        processed.getBookingId());
                alertService.sendAlert(String.format(
                        "MANUAL ACTION: Refund may be needed for booking %s, transaction %s",
                        processed.getBookingId(), processed.getTransactionId()
                ));
                break;

            case EventConstants.PAYMENT_FAILED:
                PaymentFailedEvent failed = objectMapper.readValue(payload, PaymentFailedEvent.class);
                log.warn("Dead PAYMENT_FAILED for booking {} - notification to booking service failed",
                        failed.getBookingId());
                // Payment failure notification is less critical
                break;

            default:
                log.warn("No compensation for dead event type: {}", eventType);
        }
    }

    private void compensateDeadRecordForIncomingEvents(PaymentOutbox record) throws Exception {
        String eventType = record.getEventType();
        String payload = record.getPayload();

        switch (eventType) {
            case EventConstants.PAYMENT_REQUESTED:
                PaymentRequestedEvent requested =
                        objectMapper.readValue(payload, PaymentRequestedEvent.class);
                PaymentFailedEvent failure = new PaymentFailedEvent();
                failure.setBookingId(requested.getBookingId());
                failure.setEventId(UUID.randomUUID().toString());
                failure.setProducer("payment-service");
                failure.setCorrelationId(requested.getCorrelationId());
                failure.setCausationId(requested.getEventId());
                failure.setSchemaVersion(1);
                failure.setOccurredAt(Instant.now());
                failure.setFailureReason("Payment request exhausted all retries");
                failure.setErrorCode("PAYMENT_RETRY_EXHAUSTED");
                failure.setFailedAt(LocalDateTime.now());
                failure.setAttemptedAmount(requested.getGrandTotal());
                failure.setCurrency(requested.getCurrency());
                outgoingService.createRecord(
                        requested.getBookingId(),
                        EventConstants.PAYMENT_FAILED,
                        failure.getProducer(),
                        failure.getEventId(),
                        failure);
                break;
            case EventConstants.PAYMENT_PROCESSED:
                PaymentProcessedEvent processed = objectMapper.readValue(payload, PaymentProcessedEvent.class);
                log.warn("Dead PAYMENT_PROCESSED for booking {} - manual refund may be needed",
                        processed.getBookingId());
                alertService.sendAlert(String.format(
                        "MANUAL ACTION: Refund may be needed for booking %s, transaction %s",
                        processed.getBookingId(), processed.getTransactionId()
                ));
                break;

            case EventConstants.PAYMENT_FAILED:
                PaymentFailedEvent failed = objectMapper.readValue(payload, PaymentFailedEvent.class);
                log.warn("Dead PAYMENT_FAILED for booking {} - notification to booking service failed",
                        failed.getBookingId());
                // Payment failure notification is less critical
                break;
            default:
                log.warn("No compensation for dead event type: {}", eventType);
        }
    }
}