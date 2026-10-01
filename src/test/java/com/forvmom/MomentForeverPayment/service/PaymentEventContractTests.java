package com.forvmom.MomentForeverPayment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.events.PaymentInitiatedEvent;
import com.forvmom.MomentForeverPayment.events.PaymentRequestedEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentEventContractTests {

    @Test
    void initiatedEventCarriesIdentityAndSerializes() throws Exception {
        PaymentRequestedEvent request = new PaymentRequestedEvent();
        request.setBookingId("booking-1");
        request.setEventId("event-1");
        request.setProducer("BOOKING_SERVICE");
        request.setCorrelationId("correlation-1");

        Payment payment = new Payment();
        payment.setProvider("STRIPE");
        payment.setProviderSessionId("cs_123");
        payment.setPaymentUrl("https://checkout.stripe.com/c/pay/cs_123");

        PaymentInitiatedEvent event = (PaymentInitiatedEvent)
                new PaymentInitiatedEventFactory().createEvent(request, payment);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertNotEquals("event-1", json.get("eventId").asText());
        assertEquals("payment-service", json.get("producer").asText());
        assertEquals("PAYMENT_INITIATED", json.get("eventType").asText());
        assertEquals("correlation-1", json.get("correlationId").asText());
        assertEquals("event-1", json.get("causationId").asText());
    }

    @Test
    void missingPaymentTypeUsesConfiguredGateway() {
        PaymentStrategy stripe = mock(PaymentStrategy.class);
        when(stripe.getSupportedPaymentType()).thenReturn("STRIPE");

        PaymentProcessorRegistry registry =
                new PaymentProcessorRegistry(List.of(stripe), "stripe");

        assertSame(stripe, registry.getPaymentTypeProcessor(null));
        assertSame(stripe, registry.getPaymentTypeProcessor(""));
    }
}
