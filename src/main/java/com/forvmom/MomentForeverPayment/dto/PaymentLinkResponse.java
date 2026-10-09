package com.forvmom.MomentForeverPayment.dto;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentLinkResponse(
        String bookingId,
        String provider,
        String providerSessionId,
        String paymentUrl,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PaymentLinkResponse from(Payment payment) {
        return new PaymentLinkResponse(
                payment.getBookingId(),
                payment.getProvider(),
                payment.getProviderSessionId(),
                payment.getPaymentUrl(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
