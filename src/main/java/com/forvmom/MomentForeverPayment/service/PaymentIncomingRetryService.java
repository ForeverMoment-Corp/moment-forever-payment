package com.forvmom.MomentForeverPayment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentOutbox;
import com.forvmom.MomentForeverPayment.events.PaymentRequestedEvent;
import com.forvmom.MomentForeverPayment.repository.PaymentOutboxDao;
import com.forvmom.MomentForeverPayment.scheduler.PaymentDeadLetterHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentIncomingRetryService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIncomingRetryService.class);

    @Value("${payment.retry.max-retries:5}")
    private int maxRetries;

    @Value("${payment.retry.incoming.grace-period-minutes:2}")
    private long gracePeriodMinutes;

    private final PaymentOutboxDao outboxDao;
    private final PaymentOutboxService outboxService;
    private final PaymentProcessService paymentProcessService;
    private final ObjectMapper objectMapper;
    private final PaymentDeadLetterHandler deadLetterHandler;

    public PaymentIncomingRetryService(PaymentOutboxDao outboxDao,
                                       PaymentOutboxService outboxService,
                                       PaymentProcessService paymentProcessService,
                                       ObjectMapper objectMapper,
                                       PaymentDeadLetterHandler deadLetterHandler) {
        this.outboxDao = outboxDao;
        this.outboxService = outboxService;
        this.paymentProcessService = paymentProcessService;
        this.objectMapper = objectMapper;
        this.deadLetterHandler = deadLetterHandler;
    }

    /**
     * Retry stuck payment requests (PAYMENT_REQUESTED events)
     * Called by Quartz every 2 minutes
     */
    public void retryStuckAndFailedRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(gracePeriodMinutes);

        // Find stuck payment requests (incoming)
        List<PaymentOutbox> stuck = outboxDao.findByStatusInAndUpdatedAtBefore(
                List.of(
                        PaymentOutbox.STATUS_PROCESSING,  // Stuck during processing
                        PaymentOutbox.STATUS_FAILED,      // Previously failed
                        PaymentOutbox.STATUS_PENDING      // Never processed
                ),
                cutoff);

        if (stuck.isEmpty()) {
            return;
        }

        log.info("Found {} stuck payment requests to retry", stuck.size());

        for (PaymentOutbox outbox : stuck) {
            // Skip if already processed (safety check)
            if (PaymentOutbox.STATUS_PROCESSED.equals(outbox.getStatus())) {
                continue;
            }

            // Check max retries
            if (outbox.getRetryCount() >= maxRetries) {
                deadLetterHandler.handleDeadRecordForIncomingEvents(outbox);
                continue;
            }

            try {
                // Increment retry count and mark as PROCESSING
                outbox = outboxService.incrementRetry(outbox);

                String producer = outbox.getProducer();
                String eventId = outbox.getEventId();

                log.info("Reprocessing payment for producer={}, eventId={}", producer, eventId);
                // Deserialize the original event
                PaymentRequestedEvent event = objectMapper.readValue(
                        outbox.getPayload(),
                        PaymentRequestedEvent.class
                );
                // Process payment again (this will create a new outgoing record)
                // Using the specific transactional method for Quartz jobs (doesn't deal with Kafka Acks)
                paymentProcessService.executePaymentTransaction(outbox, event);
                log.info("Successfully retried payment for producer={}, eventId={}", producer, eventId);

            } catch (Exception e) {
                log.error("Retry failed for payment outbox id={}, producer={}, eventId={}",
                        outbox.getId(), outbox.getProducer(), outbox.getEventId(), e);
                outboxService.markAsFailed(outbox);
            }
        }
    }
}