package com.forvmom.MomentForeverPayment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentOutbox;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.repository.PaymentOutboxDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class PaymentOutboxService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxService.class);

    private final PaymentOutboxDao outboxDao;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PaymentOutboxService(
            PaymentOutboxDao outboxDao,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate) {
        this.outboxDao = outboxDao;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public PaymentOutbox findOrCreateForEvent(InboundPaymentEvent inboundPaymentEvent) {
        String eventId = inboundPaymentEvent.getEventId();
        String producer = inboundPaymentEvent.getProducer();

        Optional<PaymentOutbox> existing = outboxDao.findByProducerAndEventId(producer, eventId);
        if (existing.isPresent()) {
            log.debug("Found existing outbox record for producer={}, eventId={}", producer, eventId);
            return existing.get();
        }

        try {
            return transactionTemplate.execute(status -> createNewOutbox(inboundPaymentEvent));
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent insert detected for producer={}, eventId={}, fetching existing",
                    producer, eventId);
            return outboxDao.findByProducerAndEventId(producer, eventId)
                    .orElseThrow(() -> new IllegalStateException("Failed to recover from concurrent insert", e));
        }
    }

    private PaymentOutbox createNewOutbox(InboundPaymentEvent inboundPaymentEvent) {
        try {
            PaymentOutbox outbox = new PaymentOutbox();
            outbox.setBookingId(inboundPaymentEvent.getBookingId());
            outbox.setEventType(inboundPaymentEvent.getEventType());
            outbox.setEventId(inboundPaymentEvent.getEventId());
            outbox.setProducer(inboundPaymentEvent.getProducer());
            outbox.setStatus(PaymentOutbox.STATUS_PENDING);
            outbox.setRetryCount(0);
            outbox.setPayload(objectMapper.writeValueAsString(inboundPaymentEvent));

            PaymentOutbox saved = outboxDao.saveAndFlush(outbox);
            log.info("Created new outbox record id={} for bookingId={}, inboundPaymentEventType={}",
                    saved.getId(), inboundPaymentEvent.getBookingId(), inboundPaymentEvent.getEventType());
            return saved;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize inboundPaymentEvent payload for bookingId={}", inboundPaymentEvent.getBookingId(), e);
            throw new RuntimeException("Failed to create outbox record", e);
        }
    }

    @Transactional
    public PaymentOutbox markAsProcessing(PaymentOutbox outbox) {
        outbox.setStatus(PaymentOutbox.STATUS_PROCESSING);
        PaymentOutbox saved = outboxDao.saveAndFlush(outbox);
        log.debug("Marked outbox id={} as PROCESSING", outbox.getId());
        return saved;
    }

    @Transactional
    public void markAsProcessed(PaymentOutbox outbox) {
        outbox.setStatus(PaymentOutbox.STATUS_PROCESSED);
        outboxDao.save(outbox);
        log.debug("Marked outbox id={} as PROCESSED", outbox.getId());
    }

    @Transactional
    public void markAsFailed(PaymentOutbox outbox) {
        outbox.setStatus(PaymentOutbox.STATUS_FAILED);
        outboxDao.save(outbox);
        log.debug("Marked outbox id={} as FAILED", outbox.getId());
    }

    @Transactional
    public void markAsDead(PaymentOutbox outbox) {
        outbox.setStatus(PaymentOutbox.STATUS_DEAD);
        outboxDao.save(outbox);
        log.error("Marked outbox id={} as DEAD after max retries", outbox.getId());
    }

    @Transactional
    public PaymentOutbox incrementRetry(PaymentOutbox outbox) {
        outbox.setRetryCount(outbox.getRetryCount() + 1);
        outbox.setStatus(PaymentOutbox.STATUS_PROCESSING);
        PaymentOutbox saved = outboxDao.saveAndFlush(outbox);
        log.debug("Incremented retry count to {} for outbox id={}", outbox.getRetryCount(), outbox.getId());
        return saved;
    }
}