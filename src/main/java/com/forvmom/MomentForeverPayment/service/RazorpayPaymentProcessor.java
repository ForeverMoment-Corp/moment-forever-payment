package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.events.PaymentRequestedEvent;
import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Service dedicated to interacting with the Razorpay API.
 * Mirrors {@link StripePaymentProcessor}'s responsibilities: translate our domain models
 * into Razorpay requests and capture the hosted payment page details.
 * <p>
 * Razorpay has no direct equivalent of a Stripe Checkout Session, so this uses the
 * Razorpay "Payment Links" API, which also returns a single hosted payment page URL
 * and supports webhook-based completion/expiry notifications - the closest match to
 * how the rest of this service (Payment entity, webhook processing) already works.
 */
@Service
public class RazorpayPaymentProcessor implements PaymentStrategy {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentProcessor.class);

    @Value("${payment.gateway.razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${payment.gateway.razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${payment.gateway.razorpay.callback-url:http://localhost:8082/payment/success}")
    private String callbackUrl;

    @Override
    public Payment initiatePayment(InboundPaymentEvent inboundPaymentEvent) {
        if (razorpayKeyId.isBlank() || razorpayKeySecret.isBlank()) {
            throw new IllegalStateException("RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET must be configured");
        }

        Payment payment = new Payment();
        payment.setProvider("RAZORPAY");
        payment.setBookingId(inboundPaymentEvent.getBookingId());
        payment.setCorrelationId(inboundPaymentEvent.getCorrelationId());
        payment.setStatus(PaymentStatus.PENDING);

        if (inboundPaymentEvent instanceof PaymentRequestedEvent requestedEvent) {
            payment.setAmount(requestedEvent.getGrandTotal());
            payment.setCurrency(requestedEvent.getCurrency());
        }

        try {
            RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            // Razorpay Payment Links primarily support INR (India/Malaysia/Singapore
            // merchant accounts); default to INR when the event didn't carry a currency.
            String currency = payment.getCurrency() != null ? payment.getCurrency().toUpperCase() : "INR";
            // Razorpay amounts are expressed in the smallest currency sub-unit (e.g. paise for INR).
            long amountInSubUnits = payment.getAmount() != null
                    ? payment.getAmount().multiply(new BigDecimal(100)).longValue()
                    : 5000L;

            JSONObject paymentLinkRequest = new JSONObject();
            paymentLinkRequest.put("amount", amountInSubUnits);
            paymentLinkRequest.put("currency", currency);
            paymentLinkRequest.put("description", "Booking " + payment.getBookingId());
            paymentLinkRequest.put("reference_id", payment.getBookingId());
            paymentLinkRequest.put("callback_url", callbackUrl);
            paymentLinkRequest.put("callback_method", "get");

            // Payment Links have no client-supplied idempotency key parameter, unlike
            // Stripe Checkout Sessions. Duplicate-request protection is already handled
            // upstream by PaymentOutboxService's inbox/outbox tables, so we only stash
            // the Kafka event id in notes for traceability when inspecting the Razorpay
            // dashboard.
            JSONObject notes = new JSONObject();
            notes.put("eventId", inboundPaymentEvent.getEventId());
            paymentLinkRequest.put("notes", notes);

            PaymentLink paymentLink = razorpayClient.paymentLink.create(paymentLinkRequest);
            String paymentLinkId = paymentLink.get("id");
            String shortUrl = paymentLink.get("short_url");

            payment.setProviderSessionId(paymentLinkId);
            payment.setPaymentUrl(shortUrl);

            log.info("Created Razorpay Payment Link: {} for booking: {}",
                    paymentLinkId, payment.getBookingId());

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay Payment Link", e);
            throw new IllegalStateException("Razorpay Payment Link creation failed", e);
        }

        return payment;
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.RAZORPAY;
    }
}
