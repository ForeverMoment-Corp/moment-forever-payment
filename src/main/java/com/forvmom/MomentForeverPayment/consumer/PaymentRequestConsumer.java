package com.forvmom.MomentForeverPayment.consumer;

import com.forvmom.MomentForeverPayment.events.PaymentRequestedEvent;
import com.forvmom.MomentForeverPayment.scheduler.OutgoingPaymentPublisher;
import com.forvmom.MomentForeverPayment.service.PaymentOutboxService;
import com.forvmom.MomentForeverPayment.service.PaymentProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestConsumer.class);

    private final PaymentProcessService paymentProcessService;
    private final PaymentOutboxService outboxService;
    private final OutgoingPaymentPublisher outgoingPublisher;

    public PaymentRequestConsumer(PaymentProcessService paymentProcessService,
                                  PaymentOutboxService outboxService,
                                  OutgoingPaymentPublisher outgoingPublisher) {
        this.paymentProcessService = paymentProcessService;
        this.outboxService = outboxService;
        this.outgoingPublisher = outgoingPublisher;
    }

    @KafkaListener(topics = "${kafka.topics.payment-requested}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentRequested(@Payload PaymentRequestedEvent paymentRequestedEvent, Acknowledgment ack) {

        paymentProcessService.handleIncomingPaymentRequest(paymentRequestedEvent, ack);

    }
}