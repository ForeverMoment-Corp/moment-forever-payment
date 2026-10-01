package com.forvmom.MomentForeverPayment.producer;


import com.forvmom.MomentForeverPayment.events.PaymentFailedEvent;
import com.forvmom.MomentForeverPayment.events.PaymentProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.payment-processed}")
    private String paymentProcessedTopic;

    @Value("${kafka.topics.payment-failed}")
    private String paymentFailedTopic;
    
    @Value("${kafka.topics.payment-initiated:payment-initiated-topic}")
    private String paymentInitiatedTopic;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    public void sendPaymentInitiatedEvent(com.forvmom.MomentForeverPayment.events.PaymentInitiatedEvent event)
            throws Exception {
        kafkaTemplate.send(paymentInitiatedTopic, event.getBookingId(), event).get(10, TimeUnit.SECONDS);
        log.info("Sent PaymentInitiatedEvent: bookingId={}, paymentUrl={}",
                event.getBookingId(), event.getPaymentUrl());
    }

    public void sendPaymentProcessedEvent(PaymentProcessedEvent event) throws Exception {
        kafkaTemplate.send(paymentProcessedTopic, event.getBookingId(), event).get(10, TimeUnit.SECONDS);
        log.info("Sent PaymentProcessedEvent: bookingId={}, transactionId={}",
                event.getBookingId(), event.getTransactionId());
    }

    public void sendPaymentFailedEvent(PaymentFailedEvent event) throws Exception {
        kafkaTemplate.send(paymentFailedTopic, event.getBookingId(), event).get(10, TimeUnit.SECONDS);
        log.info("Sent PaymentFailedEvent: bookingId={}, reason={}",
                event.getBookingId(), event.getFailureReason());
    }
}