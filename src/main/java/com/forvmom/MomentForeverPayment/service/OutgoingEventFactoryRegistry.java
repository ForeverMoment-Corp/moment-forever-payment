package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.commons.EventConstants;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry to resolve the correct OutgoingEventFactory based on the current PaymentStatus.
 */
@Component
public class OutgoingEventFactoryRegistry {

    private final Map<String, OutgoingEventFactory> factoryMap;

    public OutgoingEventFactoryRegistry(List<OutgoingEventFactory> factories) {
        factoryMap = factories.stream()
                .collect(Collectors.toMap(
                        OutgoingEventFactory::getSupportedEventType,
                        Function.identity()
                ));
    }

    /**
     * Resolves the appropriate factory based on the status of the payment.
     * Uses the status to determine whether to map to an INITIATED, PROCESSED, or FAILED event.
     */
    public OutgoingEventFactory getFactoryForStatus(PaymentStatus status) {
        String eventType = status == PaymentStatus.PENDING ? EventConstants.PAYMENT_INITIATED : 
                           status == PaymentStatus.SUCCESS ? EventConstants.PAYMENT_PROCESSED : 
                           EventConstants.PAYMENT_FAILED;
        OutgoingEventFactory factory = factoryMap.get(eventType);
        if (factory == null) {
            throw new IllegalArgumentException("No factory found for event type: " + eventType);
        }
        return factory;
    }
}
