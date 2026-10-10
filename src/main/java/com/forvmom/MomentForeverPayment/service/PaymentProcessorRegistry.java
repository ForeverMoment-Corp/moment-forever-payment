package com.forvmom.MomentForeverPayment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dynamically selects the {@link PaymentStrategy} for a payment.
 *
 * <p>Spring injects every {@link PaymentStrategy} bean into the list; this
 * factory indexes them by {@link PaymentProvider}. Selection precedence:
 * <ol>
 *   <li>explicit {@code paymentType} carried by the inbound event, or</li>
 *   <li>the {@code payment.gateway.provider} application property
 *       (env {@code PAYMENT_GATEWAY_PROVIDER}, default {@code stripe}).</li>
 * </ol>
 * To switch providers, change just that property — no code change. Adding a
 * new provider means adding one strategy bean; this factory needs no edit.
 */
@Service
public class PaymentProcessorRegistry {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorRegistry.class);

    private final Map<PaymentProvider, PaymentStrategy> paymentStrategyMap;
    private final PaymentProvider defaultProvider;

    public PaymentProcessorRegistry(
            List<PaymentStrategy> processors,
            @Value("${payment.gateway.provider}") String defaultProvider) {
        paymentStrategyMap = processors.stream()
                .collect(Collectors.toMap(
                        PaymentStrategy::getProvider,
                        Function.identity()
                ));
        // Fail fast at startup on a misconfigured provider name.
        this.defaultProvider = PaymentProvider.from(defaultProvider);
        log.info("Payment default provider: {} (available: {})",
                this.defaultProvider, paymentStrategyMap.keySet());
    }


    public PaymentStrategy getPaymentTypeProcessor(String paymentType) {
        PaymentProvider provider = paymentType == null || paymentType.isBlank()
                ? defaultProvider
                : PaymentProvider.from(paymentType);
        PaymentStrategy strategy = paymentStrategyMap.get(provider);
        if (strategy == null) {
            throw new IllegalArgumentException("No payment strategy found for type: " + provider);
        }
        return strategy;
    }

    public PaymentProvider getDefaultProvider() {
        return defaultProvider;
    }
}
