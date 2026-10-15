package com.forvmom.MomentForeverPayment.mapper;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.dto.response.PaymentResponse;

/**
 * Entity-to-DTO projection for payment read APIs.
 * Must be called inside the service transaction (same rule as
 * booking-service {@code BookingMapper}).
 */
public final class PaymentMapper {

    private PaymentMapper() {
        // Static projections only.
    }

    public static PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.from(payment);
    }
}
