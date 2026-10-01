package com.forvmom.MomentForeverPayment.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentProcessorRegistry {


    private final Map<String, PaymentStrategy> paymentStrategyMap;
    private final String defaultProvider;

    public PaymentProcessorRegistry(
            List<PaymentStrategy> processors,
            @Value("${payment.gateway.provider:stripe}") String defaultProvider) {
        paymentStrategyMap = processors.stream()
                .collect(Collectors.toMap(
                        PaymentStrategy::getSupportedPaymentType,
                        Function.identity()
                ));
        this.defaultProvider = defaultProvider.toUpperCase();
    }


    public PaymentStrategy getPaymentTypeProcessor(String paymentType) {
        String provider = paymentType == null || paymentType.isBlank()
                ? defaultProvider
                : paymentType.toUpperCase();
        PaymentStrategy strategy = paymentStrategyMap.get(provider);
        if (strategy == null) {
            throw new IllegalArgumentException("No payment strategy found for type: " + provider);
        }
        return strategy;
    }

}
