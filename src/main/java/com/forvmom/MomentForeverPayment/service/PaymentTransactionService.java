package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentOutbox;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.events.OutGoingEvent;
import com.forvmom.MomentForeverPayment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentTransactionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentTransactionService.class);

    private final PaymentRepository paymentRepository;
    private final OutgoingEventFactoryRegistry outgoingEventFactoryRegistry;
    private final OutgoingPaymentOutboxService outgoingPaymentOutboxService;
    private final PaymentOutboxService paymentOutboxService;

    public PaymentTransactionService(
            PaymentRepository paymentRepository,
            OutgoingEventFactoryRegistry outgoingEventFactoryRegistry,
            OutgoingPaymentOutboxService outgoingPaymentOutboxService,
            PaymentOutboxService paymentOutboxService) {
        this.paymentRepository = paymentRepository;
        this.outgoingEventFactoryRegistry = outgoingEventFactoryRegistry;
        this.outgoingPaymentOutboxService = outgoingPaymentOutboxService;
        this.paymentOutboxService = paymentOutboxService;
    }

    @Transactional
    public OutgoingPaymentOutbox persistPaymentResult(
            PaymentOutbox inbox,
            InboundPaymentEvent inboundEvent,
            Payment payment) {
        Payment savedPayment = paymentRepository.save(payment);
        OutgoingEventFactory factory =
                outgoingEventFactoryRegistry.getFactoryForStatus(savedPayment.getStatus());
        OutGoingEvent outgoingEvent = factory.createEvent(inboundEvent, savedPayment);

        OutgoingPaymentOutbox outgoingRecord = outgoingPaymentOutboxService.createRecord(
                inbox.getBookingId(),
                outgoingEvent.getEventType(),
                outgoingEvent.getProducer(),
                outgoingEvent.getEventId(),
                outgoingEvent);

        paymentOutboxService.markAsProcessed(inbox);
        log.info("Persisted payment result and outgoing event for bookingId={}",
                inboundEvent.getBookingId());
        return outgoingRecord;
    }
}
