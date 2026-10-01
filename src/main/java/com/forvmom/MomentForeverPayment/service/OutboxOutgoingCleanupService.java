package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.repository.OutgoingPaymentOutboxDao;
import com.forvmom.MomentForeverPayment.repository.PaymentOutboxDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxOutgoingCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(OutboxOutgoingCleanupService.class);
    @Value("${payment.cleanup.outgoing.hours-to-keep:24}")
    private int hoursToKeep;

    private final OutgoingPaymentOutboxDao outgoingPaymentOutboxDao;


    public OutboxOutgoingCleanupService(OutgoingPaymentOutboxDao outgoingPaymentOutboxDao) {
        this.outgoingPaymentOutboxDao = outgoingPaymentOutboxDao;
    }

    // Cleanup old successfully published records.
    @Transactional
    public void cleanupPublishedRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hoursToKeep);
        int deleted = outgoingPaymentOutboxDao.deleteByStatusAndUpdatedAtBefore(
                OutgoingPaymentOutbox.STATUS_SENT, cutoff);
        if (deleted > 0) {
            logger.info("Cleaned up {} processed records older than {}", deleted, cutoff);
        }
    }
}