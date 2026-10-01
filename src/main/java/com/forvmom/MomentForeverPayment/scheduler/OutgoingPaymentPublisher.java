package com.forvmom.MomentForeverPayment.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forvmom.MomentForeverPayment.commons.EventConstants;
import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.events.PaymentFailedEvent;
import com.forvmom.MomentForeverPayment.events.PaymentProcessedEvent;
import com.forvmom.MomentForeverPayment.producer.PaymentEventProducer;
import com.forvmom.MomentForeverPayment.repository.OutgoingPaymentOutboxDao;
import com.forvmom.MomentForeverPayment.service.OutgoingPaymentOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutgoingPaymentPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutgoingPaymentPublisher.class);
    private static final int MAX_RETRIES = 5;
    private static final long GRACE_PERIOD_MINUTES = 1;

    private final OutgoingPaymentOutboxDao outgoingDao;
    private final PaymentEventProducer eventProducer;
    private final ObjectMapper objectMapper;
    private final OutgoingPaymentOutboxService outgoingService;
    private final PaymentDeadLetterHandler deadLetterHandler;

    public OutgoingPaymentPublisher(OutgoingPaymentOutboxDao outgoingDao,
                                    PaymentEventProducer eventProducer,
                                    ObjectMapper objectMapper,
                                    OutgoingPaymentOutboxService outgoingService,
                                    PaymentDeadLetterHandler deadLetterHandler) {
        this.outgoingDao = outgoingDao;
        this.eventProducer = eventProducer;
        this.objectMapper = objectMapper;
        this.outgoingService = outgoingService;
        this.deadLetterHandler = deadLetterHandler;
    }

    // Immediate publish attempt (called from consumer)
    public void trySinglePublish(OutgoingPaymentOutbox record) {
        if (record == null) return;

        try {
            sendToKafka(record);
            outgoingService.markAsSent(record);
            log.info("Immediate publish succeeded: id={}, type={}, bookingId={}",
                    record.getId(), record.getEventType(), record.getBookingId());
        } catch (Exception e) {
            log.warn("Immediate publish failed for record id={}, will retry via Quartz: {}",
                    record.getId(), e.getMessage());
            outgoingService.markAsFailed(record);
        }
    }

    // Batch retry (called by Quartz)
    public void publishPendingEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(GRACE_PERIOD_MINUTES);
        List<OutgoingPaymentOutbox> pending = outgoingDao.findByStatusInAndUpdatedAtBefore(
                List.of(
                    OutgoingPaymentOutbox.STATUS_PENDING, 
                    OutgoingPaymentOutbox.STATUS_FAILED,
                    OutgoingPaymentOutbox.STATUS_PROCESSING // Pick up stuck PROCESSING records older than cutoff
                ),
                cutoff);

        if (!pending.isEmpty()) {
            log.info("Quartz found {} outgoing records to retry", pending.size());
        }

        for (OutgoingPaymentOutbox record : pending) {
            if (record.getRetryCount() >= MAX_RETRIES) {
                deadLetterHandler.handleDeadRecordForOutgoingEvents(record);
                continue;
            }

            try {
                // This correctly locks the record to PROCESSING before sending to Kafka
                record = outgoingService.incrementRetry(record);
                
                sendToKafka(record);
                outgoingService.markAsSent(record);
                
                log.info("Quartz retry succeeded: id={}, type={}, bookingId={}",
                        record.getId(), record.getEventType(), record.getBookingId());
            } catch (Exception e) {
                log.error("Quartz retry failed for id={}: {}", record.getId(), e.getMessage());
                // Mark it back to failed so Quartz can pick it up again later
                outgoingService.markAsFailed(record);
            }
        }
    }

    private void sendToKafka(OutgoingPaymentOutbox record) throws Exception {
        String type = record.getEventType();
        String json = record.getPayload();

        switch (type) {
            case EventConstants.PAYMENT_INITIATED:
                com.forvmom.MomentForeverPayment.events.PaymentInitiatedEvent initiatedEvent = objectMapper.readValue(json, com.forvmom.MomentForeverPayment.events.PaymentInitiatedEvent.class);
                eventProducer.sendPaymentInitiatedEvent(initiatedEvent);
                break;
                
            case EventConstants.PAYMENT_PROCESSED:
                PaymentProcessedEvent processedEvent = objectMapper.readValue(json, PaymentProcessedEvent.class);
                eventProducer.sendPaymentProcessedEvent(processedEvent);
                break;

            case EventConstants.PAYMENT_FAILED:
                PaymentFailedEvent failedEvent = objectMapper.readValue(json, PaymentFailedEvent.class);
                eventProducer.sendPaymentFailedEvent(failedEvent);
                break;

            default:
                throw new IllegalArgumentException("Unknown outgoing event type: " + type);
        }
    }
}