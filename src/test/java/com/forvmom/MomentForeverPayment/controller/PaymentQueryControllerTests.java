package com.forvmom.MomentForeverPayment.controller;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.dto.response.PaymentResponse;
import com.forvmom.MomentForeverPayment.service.PaymentQueryService;
import com.forvmom.MomentForeverPayment.exception.PaymentGlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentQueryControllerTests {

    @Mock
    private PaymentQueryService paymentQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentQueryController(paymentQueryService))
                .setControllerAdvice(new PaymentGlobalExceptionHandler())
                .build();
    }

    private Payment payment(Long id, String bookingId, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setBookingId(bookingId);
        payment.setProvider("STRIPE");
        payment.setProviderSessionId("cs_" + id);
        payment.setPaymentUrl("https://pay.example/" + id);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(status);
        return payment;
    }

    @Test
    void getPaymentByIdReturnsFullDetails() throws Exception {
        when(paymentQueryService.getPaymentById(1L))
                .thenReturn(PaymentResponse.from(payment(1L, "BK-1", PaymentStatus.SUCCESS)));

        mockMvc.perform(get("/admin/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value("BK-1"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void getPaymentsByBookingIdReturnsAttempts() throws Exception {
        when(paymentQueryService.getPaymentsByBookingId("BK-1"))
                .thenReturn(List.of(
                        PaymentResponse.from(payment(2L, "BK-1", PaymentStatus.PENDING)),
                        PaymentResponse.from(payment(1L, "BK-1", PaymentStatus.FAILED))));

        mockMvc.perform(get("/admin/payments/booking/BK-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bookingId").value("BK-1"));
    }

    @Test
    void listAllPaymentsIsPaged() throws Exception {
        when(paymentQueryService.getAllPayments(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(PaymentResponse.from(payment(1L, "BK-1", PaymentStatus.PENDING)))));

        mockMvc.perform(get("/admin/payments").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void filterPaymentsPassesThrough() throws Exception {
        when(paymentQueryService.searchPayments(any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(PaymentResponse.from(payment(1L, "BK-1", PaymentStatus.SUCCESS)))));

        mockMvc.perform(get("/admin/payments/filter").param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));
    }

    @Test
    void rejectsInvalidSortField() throws Exception {
        mockMvc.perform(get("/admin/payments").param("sort", "password,desc"))
                .andExpect(status().isBadRequest());
    }
}
