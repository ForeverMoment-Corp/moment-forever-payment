package com.forvmom.MomentForeverPayment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.repository.OutgoingPaymentOutboxDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutgoingPaymentOutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutgoingPaymentOutboxService.class);

    private final OutgoingPaymentOutboxDao outgoingDao;
    private final ObjectMapper objectMapper;

    public OutgoingPaymentOutboxService(OutgoingPaymentOutboxDao outgoingDao, ObjectMapper objectMapper) {
        this.outgoingDao = outgoingDao;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OutgoingPaymentOutbox createRecord(String bookingId, String eventType, String producer, String eventId, Object payload) {
        try {
            OutgoingPaymentOutbox record = new OutgoingPaymentOutbox();
            record.setBookingId(bookingId);
            record.setEventType(eventType);
            record.setProducer(producer);
            record.setEventId(eventId);
            record.setStatus(OutgoingPaymentOutbox.STATUS_PENDING);
            record.setRetryCount(0);
            record.setPayload(objectMapper.writeValueAsString(payload));

            OutgoingPaymentOutbox saved = outgoingDao.save(record);
            log.info("Created outgoing outbox record id={}, type={}, bookingId={}",
                    saved.getId(), eventType, bookingId);
            return saved;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outgoing event payload for bookingId={}", bookingId, e);
            throw new RuntimeException("Failed to create outgoing outbox record", e);
        }
    }

    @Transactional
    public void markAsSent(OutgoingPaymentOutbox record) {
        record.setStatus(OutgoingPaymentOutbox.STATUS_SENT);
        outgoingDao.save(record);
        log.debug("Marked outgoing outbox id={} as SENT", record.getId());
    }

    @Transactional
    public void markAsFailed(OutgoingPaymentOutbox record) {
        record.setStatus(OutgoingPaymentOutbox.STATUS_FAILED);
        outgoingDao.save(record);
        log.debug("Marked outgoing outbox id={} as FAILED", record.getId());
    }

    @Transactional
    public void markAsDead(OutgoingPaymentOutbox record) {
        record.setStatus(OutgoingPaymentOutbox.STATUS_DEAD);
        outgoingDao.save(record);
        log.error("Marked outgoing outbox id={} as DEAD", record.getId());
    }

    @Transactional
    public OutgoingPaymentOutbox incrementRetry(OutgoingPaymentOutbox record) {
        record.setRetryCount(record.getRetryCount() + 1);
        record.setStatus(OutgoingPaymentOutbox.STATUS_PROCESSING); // Lock it to processing during Kafka attempt
        OutgoingPaymentOutbox saved = outgoingDao.saveAndFlush(record);
        log.debug("Incremented retry count to {} for outgoing outbox id={}",
                record.getRetryCount(), record.getId());
        return saved;
    }
}