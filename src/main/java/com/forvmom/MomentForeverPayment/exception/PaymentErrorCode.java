package com.forvmom.MomentForeverPayment.exception;

/**
 * Application-level error codes for the payment service.
 * Format: PAYMENT_&lt;DOMAIN&gt;_&lt;REASON&gt; (mirrors booking-service).
 */
public enum PaymentErrorCode {
    PAYMENT_NOT_FOUND,
    PAYMENT_NOT_READY,
    VALIDATION_FAILED,
    INVALID_REQUEST,
    UNAUTHORIZED,
    INTERNAL_SERVER_ERROR,
    SERVICE_UNAVAILABLE
}
