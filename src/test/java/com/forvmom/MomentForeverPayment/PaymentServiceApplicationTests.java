package com.forvmom.MomentForeverPayment;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.events.PaymentRequestedEvent;
import com.forvmom.MomentForeverPayment.repository.OutgoingPaymentOutboxDao;
import com.forvmom.MomentForeverPayment.repository.PaymentOutboxDao;
import com.forvmom.MomentForeverPayment.repository.PaymentRepository;
import com.forvmom.MomentForeverPayment.scheduler.OutgoingPaymentPublisher;
import com.forvmom.MomentForeverPayment.service.PaymentProcessService;
import com.forvmom.MomentForeverPayment.service.PaymentProcessorRegistry;
import com.forvmom.MomentForeverPayment.service.PaymentStrategy;
import com.forvmom.MomentForeverPayment.service.PaymentTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceApplicationTests {

    @Autowired
    private PaymentTransactionService paymentTransactionService;

    @Autowired
    private PaymentProcessService paymentProcessService;

    @Autowired
    private PaymentOutboxDao paymentOutboxDao;

    @Autowired
    private OutgoingPaymentOutboxDao outgoingPaymentOutboxDao;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private PaymentProcessorRegistry paymentProcessorRegistry;

    @MockBean
    private OutgoingPaymentPublisher outgoingPaymentPublisher;

    @Test
    void contextLoads() {
        assertTrue(AopUtils.isAopProxy(paymentTransactionService),
                "The atomic payment persistence service must be transaction-proxied");
    }

    @Test
    void paymentRequestPersistsInboxPaymentAndOutgoingEvent() {
        PaymentStrategy strategy = mock(PaymentStrategy.class);
        Payment payment = new Payment();
        payment.setProvider("STRIPE");
        payment.setProviderSessionId("cs_integration_1");
        payment.setPaymentUrl("https://checkout.stripe.com/c/pay/cs_integration_1");
        payment.setAmount(new BigDecimal("125.00"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.PENDING);
        when(strategy.initiatePayment(any())).thenReturn(payment);
        when(paymentProcessorRegistry.getPaymentTypeProcessor(null)).thenReturn(strategy);

        PaymentRequestedEvent request = new PaymentRequestedEvent();
        request.setBookingId("booking-integration-1");
        request.setEventId("event-integration-1");
        request.setProducer("booking-service");
        request.setGrandTotal(new BigDecimal("125.00"));
        request.setCurrency("INR");

        paymentProcessService.handleIncomingPaymentRequest(request, null);

        PaymentOutbox inbox = paymentOutboxDao
                .findByProducerAndEventId("booking-service", "event-integration-1")
                .orElseThrow();
        OutgoingPaymentOutbox outgoing = outgoingPaymentOutboxDao
                .findFirstByBookingIdAndEventTypeOrderByCreatedAtDesc(
                        "booking-integration-1", "PAYMENT_INITIATED")
                .orElseThrow();

        assertEquals(PaymentOutbox.STATUS_PROCESSED, inbox.getStatus());
        assertEquals(OutgoingPaymentOutbox.STATUS_PENDING, outgoing.getStatus());
        assertEquals(1, paymentRepository.count());
        verify(outgoingPaymentPublisher).trySinglePublish(any(OutgoingPaymentOutbox.class));
    }
}
