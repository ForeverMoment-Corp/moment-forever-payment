package com.forvmom.MomentForeverPayment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a payment record cannot be found. Maps to 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PaymentNotFoundException extends RuntimeException {

    private final String identifier;

    public PaymentNotFoundException(String message, String identifier) {
        super(message);
        this.identifier = identifier;
    }

    public PaymentNotFoundException(String message) {
        this(message, null);
    }

    public String getIdentifier() {
        return identifier;
    }
}
