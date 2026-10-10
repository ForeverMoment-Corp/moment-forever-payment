package com.forvmom.MomentForeverPayment.service;

import java.util.Arrays;
import java.util.Locale;

/**
 * Payment providers supported by this service.
 *
 * <p>The active provider is chosen dynamically: an inbound event may carry an
 * explicit {@code paymentType}, otherwise the {@code payment.gateway.provider}
 * application property (env {@code PAYMENT_GATEWAY_PROVIDER}) supplies the
 * default. To switch providers, change just that property — no code change.
 */
public enum PaymentProvider {
    STRIPE,
    RAZORPAY;

    /**
     * Parses a provider name case-insensitively.
     *
     * @param name e.g. "stripe", "STRIPE", "Razorpay"
     * @return matching provider
     * @throws IllegalArgumentException if blank or unsupported, listing the
     *                                  supported values
     */
    public static PaymentProvider from(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Payment provider must not be blank. Supported values: "
                            + Arrays.toString(PaymentProvider.values()));
        }
        try {
            return PaymentProvider.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported payment provider: '" + name + "'. Supported values: "
                            + Arrays.toString(PaymentProvider.values()), e);
        }
    }
}
