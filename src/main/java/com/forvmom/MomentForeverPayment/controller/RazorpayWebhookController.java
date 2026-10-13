package com.forvmom.MomentForeverPayment.controller;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.domain.entity.WebhookInbox;
import com.forvmom.MomentForeverPayment.repository.PaymentOutboxDao;
import com.forvmom.MomentForeverPayment.repository.PaymentRepository;
import com.forvmom.MomentForeverPayment.repository.WebhookInboxRepository;
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
    private final PaymentRepository paymentRepository;
    private final PaymentOutboxDao paymentOutboxDao;
    private final WebhookInboxRepository webhookInboxRepository;

    public RazorpayWebhookController(WebhookProcessingService webhookProcessingService,
                                     OutgoingPaymentPublisher outgoingPaymentPublisher,
                                     PaymentRepository paymentRepository,
                                     PaymentOutboxDao paymentOutboxDao,
                                     WebhookInboxRepository webhookInboxRepository) {
        this.webhookProcessingService = webhookProcessingService;
        this.outgoingPaymentPublisher = outgoingPaymentPublisher;
        this.paymentRepository = paymentRepository;
        this.paymentOutboxDao = paymentOutboxDao;
        this.webhookInboxRepository = webhookInboxRepository;
    }

    @PostMapping
    @Operation(summary = "Razorpay webhook callback", security = {})
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventIdHeader) {

        log.info("webhook is being called...");
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
        JSONObject paymentEntity = extractEntity(payloadObject, "payment");
        String transactionId = paymentEntity != null ? paymentEntity.optString("id", null) : null;

        // Razorpay guarantees X-Razorpay-Event-Id is unique per webhook delivery; fall back
        // to a composite key only for the rare case a delivery omits it (e.g. manual replay).
        String linkOrTx = paymentLinkEntity != null ? paymentLinkEntity.optString("id", "")
                : transactionId != null ? transactionId : "unknown";
        String webhookEventId = (eventIdHeader != null && !eventIdHeader.isBlank())
                ? eventIdHeader
                : linkOrTx + ":" + eventType;

        OutgoingPaymentOutbox outgoingRecord = null;
        log.debug("event type {}, linkOrTx {}", eventType, linkOrTx);

        if (paymentLinkEntity != null) {
            String paymentLinkId = paymentLinkEntity.optString("id", null);
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
                            "RAZORPAY", webhookEventId, paymentLinkId, transactionId,
                            PaymentStatus.FAILED,
                            "Payment link " + eventType.substring("payment_link.".length()) + " - retry allowed",
                            eventType.toUpperCase());
                    break;
                case "payment.failed":
                    // Rare: link + payment both present. Prefer link correlation, keep bank reason.
                    log.info("Processing payment.failed for Razorpay payment link: {}", paymentLinkId);
                    outgoingRecord = webhookProcessingService.processWebhookAtomically(
                            "RAZORPAY", webhookEventId, paymentLinkId, transactionId,
                            PaymentStatus.FAILED,
                            buildFailureReason(paymentEntity), buildErrorCode(paymentEntity));
                    break;
                default:
                    log.info("Unhandled Razorpay event type: {}", eventType);
            }
        } else if (paymentEntity != null && "payment.failed".equals(eventType)) {
            // Bank/server decline: payload has ONLY payment.entity (no payment_link).
            // This is the common prod failure path - must map to the PENDING attempt.
            String failureReason = buildFailureReason(paymentEntity);
            String errorCode = buildErrorCode(paymentEntity);
            String bookingId = resolveBookingId(paymentEntity);
            log.info("Processing payment.failed for booking {} (pay {})", bookingId, transactionId);
            if (bookingId == null) {
                log.error("payment.failed could not be correlated to a booking (pay {}). Acking to stop retries.",
                        transactionId);
                recordUnresolvedInbox(webhookEventId);
            } else {
                outgoingRecord = webhookProcessingService.processPaymentLevelFailure(
                        "RAZORPAY", webhookEventId, bookingId, transactionId, failureReason, errorCode);
            }
        } else {
            log.info("Unhandled Razorpay event type (no payment_link payload): {}", eventType);
        }

        if (outgoingRecord != null) {
            outgoingPaymentPublisher.trySinglePublish(outgoingRecord);
        }

        // Return a 200 OK so Razorpay knows we received the webhook successfully
        return ResponseEntity.ok("Success");
    }

    private String buildFailureReason(JSONObject paymentEntity) {
        if (paymentEntity == null) {
            return "Payment failed - retry allowed";
        }
        String desc = paymentEntity.optString("error_description", "");
        if (desc.isBlank()) {
            desc = paymentEntity.optString("description", "");
        }
        String method = paymentEntity.optString("method", "");
        String base = desc.isBlank() ? "Payment failed by bank/gateway" : desc;
        return method.isBlank() ? base : base + " (method=" + method + ")";
    }

    private String buildErrorCode(JSONObject paymentEntity) {
        if (paymentEntity == null) {
            return "PAYMENT_FAILED";
        }
        String code = paymentEntity.optString("error_code", "");
        return code.isBlank() ? "PAYMENT_FAILED" : code;
    }

    /**
     * Resolves bookingId for payment-only webhooks using notes injected at link
     * creation (bookingId -&gt; correlationId -&gt; inbound eventId -&gt; description).
     */
    private String resolveBookingId(JSONObject paymentEntity) {
        JSONObject notes = paymentEntity.optJSONObject("notes");
        if (notes != null) {
            String bookingId = notes.optString("bookingId", "");
            if (!bookingId.isBlank()) {
                return bookingId;
            }
            String correlationId = notes.optString("correlationId", "");
            if (!correlationId.isBlank()) {
                var byCorr = paymentRepository.findFirstByCorrelationIdOrderByIdDesc(correlationId);
                if (byCorr.isPresent()) {
                    return byCorr.get().getBookingId();
                }
            }
            String inboundEventId = notes.optString("eventId", "");
            if (!inboundEventId.isBlank()) {
                var outbox = paymentOutboxDao.findFirstByEventId(inboundEventId);
                if (outbox.isPresent()) {
                    return outbox.get().getBookingId();
                }
            }
        }
        // Fallback: description was set to "Booking {bookingId}" at link creation.
        String description = paymentEntity.optString("description", "");
        if (description.startsWith("Booking ")) {
            String candidate = description.substring("Booking ".length()).trim();
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private void recordUnresolvedInbox(String webhookEventId) {
        try {
            if (!webhookInboxRepository.existsByEventId(webhookEventId)) {
                WebhookInbox inbox = new WebhookInbox();
                inbox.setEventId(webhookEventId);
                inbox.setProvider("RAZORPAY");
                inbox.setStatus("UNRESOLVED");
                webhookInboxRepository.save(inbox);
            }
        } catch (Exception e) {
            log.warn("Failed to record unresolved webhook {}", webhookEventId, e);
        }
    }

    private JSONObject extractEntity(JSONObject payloadObject, String key) {
        if (payloadObject == null || !payloadObject.has(key)) {
            return null;
        }
        JSONObject wrapper = payloadObject.optJSONObject(key);
        return wrapper != null ? wrapper.optJSONObject("entity") : null;
    }
}
