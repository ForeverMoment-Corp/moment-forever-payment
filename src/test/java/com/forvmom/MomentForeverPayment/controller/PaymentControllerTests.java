package com.forvmom.MomentForeverPayment.controller;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.service.PaymentQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTests {

    @Mock
    private PaymentQueryService paymentQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentQueryService)).build();
    }

    private Payment readyPayment() {
        Payment payment = new Payment();
        payment.setBookingId("BK-1");
        payment.setProvider("STRIPE");
        payment.setProviderSessionId("cs_test_123");
        payment.setPaymentUrl("https://checkout.stripe.com/pay/cs_test_123");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("149.98"));
        payment.setCurrency("USD");
        return payment;
    }

    @Test
    void returnsPaymentLinkWhenSessionReady() throws Exception {
        when(paymentQueryService.getLatestPaymentForBooking("BK-1"))
                .thenReturn(Optional.of(readyPayment()));

        mockMvc.perform(get("/bookings/BK-1/payment-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value("BK-1"))
                .andExpect(jsonPath("$.provider").value("STRIPE"))
                .andExpect(jsonPath("$.providerSessionId").value("cs_test_123"))
                .andExpect(jsonPath("$.paymentUrl").value("https://checkout.stripe.com/pay/cs_test_123"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returnsNotReadyWhenNoPaymentRow() throws Exception {
        when(paymentQueryService.getLatestPaymentForBooking("BK-unknown"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/bookings/BK-unknown/payment-link"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_READY"));
    }

    @Test
    void returnsNotReadyWhenUrlNotPersistedYet() throws Exception {
        Payment payment = readyPayment();
        payment.setPaymentUrl(null);
        when(paymentQueryService.getLatestPaymentForBooking("BK-1"))
                .thenReturn(Optional.of(payment));

        mockMvc.perform(get("/bookings/BK-1/payment-link"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_READY"));
    }
}
