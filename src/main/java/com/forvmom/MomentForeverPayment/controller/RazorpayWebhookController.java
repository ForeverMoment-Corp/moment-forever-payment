package com.forvmom.MomentForeverPayment.controller;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.scheduler.OutgoingPaymentPublisher;
import com.forvmom.MomentForeverPayment.service.WebhookProcessingService;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import io.swagger.v3.oas.annotations.Operation;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller specifically for handling incoming Webhooks from Razorpay.
 * Mirrors {@link StripeWebhookController}'s contract (signature check -> map event ->
 * delegate to the shared {@link WebhookProcessingService}) so both providers feed the
 * same idempotent payment-status update pipeline.
 */
@RestController
@RequestMapping("/webhooks/razorpay")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    @Value("${payment.gateway.razorpay.webhook-secret:}")
    private String endpointSecret;

    private final WebhookProcessingService webhookProcessingService;
    private final OutgoingPaymentPublisher outgoingPaymentPublisher;

    public RazorpayWebhookController(WebhookProcessingService webhookProcessingService,
                                      OutgoingPaymentPublisher outgoingPaymentPublisher) {
        this.webhookProcessingService = webhookProcessingService;
        this.outgoingPaymentPublisher = outgoingPaymentPublisher;
    }

    @PostMapping
    @Operation(summary = "Razorpay webhook callback", security = {})
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventIdHeader) {

        if (endpointSecret.isBlank()) {
            log.error("Razorpay webhook secret is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Webhook endpoint is not configured");
        }

        try {
            if (!Utils.verifyWebhookSignature(payload, signature, endpointSecret)) {
                log.error("Razorpay webhook signature verification failed");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Signature Verification Failed");
            }
        } catch (RazorpayException e) {
            log.error("Razorpay webhook signature verification error", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Signature Verification Failed");
        }

        JSONObject event = new JSONObject(payload);
        String eventType = event.optString("event", "");
        JSONObject payloadObject = event.optJSONObject("payload");
        JSONObject paymentLinkEntity = extractEntity(payloadObject, "payment_link");

        if (paymentLinkEntity == null) {
            // Not a Payment Links event we care about (e.g. a plain payment/order event
            // delivered to the same endpoint). Acknowledge so Razorpay doesn't retry.
            log.info("Unhandled Razorpay event type (no payment_link payload): {}", eventType);
            return ResponseEntity.ok("Success");
        }

        String paymentLinkId = paymentLinkEntity.optString("id", null);
        // "payment" entity is only present once a Payment Link has actually been paid;
        // expired/cancelled events never include it.
        JSONObject paymentEntity = extractEntity(payloadObject, "payment");
        String transactionId = paymentEntity != null ? paymentEntity.optString("id", null) : null;

        // Razorpay guarantees X-Razorpay-Event-Id is unique per webhook delivery; fall back
        // to a composite key only for the rare case a delivery omits it (e.g. manual replay).
        String webhookEventId = (eventIdHeader != null && !eventIdHeader.isBlank())
                ? eventIdHeader
                : paymentLinkId + ":" + eventType;

        OutgoingPaymentOutbox outgoingRecord = null;

        switch (eventType) {
            case "payment_link.paid":
                log.info("Processing paid Razorpay payment link: {}", paymentLinkId);
                outgoingRecord = webhookProcessingService.processWebhookAtomically(
                        "RAZORPAY", webhookEventId, paymentLinkId, transactionId, PaymentStatus.SUCCESS);
                break;
            case "payment_link.expired":
            case "payment_link.cancelled":
                log.info("Processing {} Razorpay payment link: {}", eventType, paymentLinkId);
                outgoingRecord = webhookProcessingService.processWebhookAtomically(
                        "RAZORPAY", webhookEventId, paymentLinkId, transactionId, PaymentStatus.FAILED);
                break;
            default:
                log.info("Unhandled Razorpay event type: {}", eventType);
        }

        if (outgoingRecord != null) {
            outgoingPaymentPublisher.trySinglePublish(outgoingRecord);
        }

        // Return a 200 OK so Razorpay knows we received the webhook successfully
        return ResponseEntity.ok("Success");
    }

    private JSONObject extractEntity(JSONObject payloadObject, String key) {
        if (payloadObject == null || !payloadObject.has(key)) {
            return null;
        }
        JSONObject wrapper = payloadObject.optJSONObject(key);
        return wrapper != null ? wrapper.optJSONObject("entity") : null;
    }
}
