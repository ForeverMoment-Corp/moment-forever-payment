package com.forvmom.MomentForeverPayment.controller;

import com.forvmom.MomentForeverPayment.dto.PaymentLinkResponse;
import com.forvmom.MomentForeverPayment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping
@Tag(name = "Payment", description = "UI-facing payment APIs")
public class PaymentController {

    private final PaymentQueryService paymentQueryService;

    public PaymentController(PaymentQueryService paymentQueryService) {
        this.paymentQueryService = paymentQueryService;
    }

    @GetMapping("/bookings/{bookingId}/payment-link")
    @Operation(summary = "Get latest payment link URL for a booking. " +
            "UI polls this after creating a booking until Stripe/Razorpay session is ready.")
    public ResponseEntity<?> getPaymentLink(@PathVariable String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_BOOKING_ID",
                    "message", "bookingId must not be blank"));
        }

        return paymentQueryService.getLatestPaymentForBooking(bookingId.trim())
                .map(payment -> {
                    // Session not created yet (no URL persisted) -> tell UI to keep polling
                    if (payment.getPaymentUrl() == null || payment.getPaymentUrl().isBlank()) {
                        return ResponseEntity.status(404).body(Map.of(
                                "code", "PAYMENT_NOT_READY",
                                "message", "Payment session is being created. Retry shortly.",
                                "bookingId", bookingId));
                    }
                    return ResponseEntity.ok(PaymentLinkResponse.from(payment));
                })
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "code", "PAYMENT_NOT_READY",
                        "message", "No payment session found yet for this booking. Retry shortly.",
                        "bookingId", bookingId)));
    }
}
