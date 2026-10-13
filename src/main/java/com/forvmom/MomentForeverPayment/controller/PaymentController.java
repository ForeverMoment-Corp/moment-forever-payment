package com.forvmom.MomentForeverPayment.controller;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.dto.PaymentLinkResponse;
import com.forvmom.MomentForeverPayment.events.InboundPaymentEvent;
import com.forvmom.MomentForeverPayment.events.OutGoingEvent;
import com.forvmom.MomentForeverPayment.events.PaymentRequestedEvent;
import com.forvmom.MomentForeverPayment.repository.PaymentRepository;
import com.forvmom.MomentForeverPayment.scheduler.OutgoingPaymentPublisher;
import com.forvmom.MomentForeverPayment.service.OutgoingEventFactory;
import com.forvmom.MomentForeverPayment.service.OutgoingEventFactoryRegistry;
import com.forvmom.MomentForeverPayment.service.OutgoingPaymentOutboxService;
import com.forvmom.MomentForeverPayment.service.PaymentProcessorRegistry;
import com.forvmom.MomentForeverPayment.service.PaymentQueryService;
import com.forvmom.MomentForeverPayment.service.PaymentStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
@Tag(name = "Payment", description = "UI-facing payment APIs")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentQueryService paymentQueryService;
    private final PaymentRepository paymentRepository;
    private final PaymentProcessorRegistry processorRegistry;
    private final OutgoingEventFactoryRegistry eventFactoryRegistry;
    private final OutgoingPaymentOutboxService outgoingPaymentOutboxService;
    private final OutgoingPaymentPublisher outgoingPaymentPublisher;

    @Autowired
    public PaymentController(PaymentQueryService paymentQueryService,
                             PaymentRepository paymentRepository,
                             PaymentProcessorRegistry processorRegistry,
                             OutgoingEventFactoryRegistry eventFactoryRegistry,
                             OutgoingPaymentOutboxService outgoingPaymentOutboxService,
                             OutgoingPaymentPublisher outgoingPaymentPublisher) {
        this.paymentQueryService = paymentQueryService;
        this.paymentRepository = paymentRepository;
        this.processorRegistry = processorRegistry;
        this.eventFactoryRegistry = eventFactoryRegistry;
        this.outgoingPaymentOutboxService = outgoingPaymentOutboxService;
        this.outgoingPaymentPublisher = outgoingPaymentPublisher;
    }

    /** Test-only convenience constructor (GET payment-link path needs only query service). */
    public PaymentController(PaymentQueryService paymentQueryService) {
        this(paymentQueryService, null, null, null, null, null);
    }

    @GetMapping("/{bookingId}/payment-link")
    @Operation(summary = "Get latest payment link URL for a booking. " +
            "UI polls this after creating a booking until Stripe/Razorpay session is ready.")
    public ResponseEntity<?> getPaymentLink(@PathVariable String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_BOOKING_ID",
                    "message", "bookingId must not be blank"));
        }

        return paymentQueryService.getLatestPaymentForBooking(bookingId.trim())
                .map(payment -> {
                    // Session not created yet (no URL persisted) -> tell UI to keep polling
                    if (payment.getPaymentUrl() == null || payment.getPaymentUrl().isBlank()) {
                        return ResponseEntity.status(404).body(Map.of(
                                "code", "PAYMENT_NOT_READY",
                                "message", "Payment session is being created. Retry shortly.",
                                "bookingId", bookingId));
                    }
                    return ResponseEntity.ok(PaymentLinkResponse.from(payment));
                })
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "code", "PAYMENT_NOT_READY",
                        "message", "No payment session found yet for this booking. Retry shortly.",
                        "bookingId", bookingId)));
    }

    /**
     * Creates a fresh payment attempt after a failure (bank decline, expired or
     * cancelled link). A single PAYMENT_FAILED is retryable - it must not be treated
     * as a terminal booking failure. Each retry inserts a NEW Payment row with a new
     * providerSessionId; the old FAILED row is kept for audit and never blocks the
     * new attempt. Booking-service should keep the reservation on hold until success
     * or its own business deadline.
     */
    @PostMapping("/{bookingId}/retry")
    @Transactional
    @Operation(summary = "Retry a failed payment by issuing a new payment link. "
            + "Only allowed when the latest attempt is FAILED.")
    public ResponseEntity<?> retryPayment(@PathVariable String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_BOOKING_ID",
                    "message", "bookingId must not be blank"));
        }
        String id = bookingId.trim();
        var latestOpt = paymentRepository.findFirstByBookingIdOrderByIdDesc(id);
        if (latestOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "PAYMENT_NOT_FOUND",
                    "message", "No payment attempt found for this booking.",
                    "bookingId", id));
        }
        Payment latest = latestOpt.get();
        if (latest.getStatus() == PaymentStatus.SUCCESS) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "ALREADY_PAID",
                    "message", "Booking is already paid. Retry not allowed.",
                    "bookingId", id));
        }
        if (latest.getStatus() == PaymentStatus.PENDING) {
            // Avoid duplicate links/charges while an attempt is still in flight.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "PAYMENT_IN_PROGRESS",
                    "message", "A payment attempt is already pending. Use the existing link.",
                    "bookingId", id,
                    "paymentUrl", latest.getPaymentUrl() == null ? "" : latest.getPaymentUrl(),
                    "providerSessionId", latest.getProviderSessionId() == null ? "" : latest.getProviderSessionId()));
        }

        // Latest is FAILED -> issue a brand-new attempt reusing amount/currency/provider.
        PaymentRequestedEvent retryEvent = new PaymentRequestedEvent();
        retryEvent.setBookingId(id);
        retryEvent.setGrandTotal(latest.getAmount());
        retryEvent.setCurrency(latest.getCurrency());
        retryEvent.setPaymentType(latest.getProvider());
        retryEvent.setEventId(UUID.randomUUID().toString());
        retryEvent.setProducer("payment-service");
        retryEvent.setCorrelationId(latest.getCorrelationId());
        retryEvent.setOccurredAt(Instant.now());

        PaymentStrategy strategy = processorRegistry.getPaymentTypeProcessor(latest.getProvider());
        Payment fresh;
        try {
            fresh = strategy.initiatePayment(retryEvent);
        } catch (Exception e) {
            log.error("Payment retry failed for booking {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "code", "RETRY_FAILED",
                    "message", "Could not create a new payment link: " + e.getMessage(),
                    "bookingId", id));
        }
        // initiatePayment builds a transient Payment; persist it as the new latest attempt.
        Payment saved = paymentRepository.save(fresh);

        InboundPaymentEvent syntheticInbound = new InboundPaymentEvent() {
            @Override public String getEventType() { return "PAYMENT_RETRY"; }
            @Override public String getBookingId() { return id; }
            @Override public String getPaymentType() { return saved.getProvider(); }
            @Override public String getEventId() { return retryEvent.getEventId(); }
            @Override public String getProducer() { return "payment-service"; }
            @Override public String getCorrelationId() { return saved.getCorrelationId(); }
        };
        OutgoingEventFactory factory = eventFactoryRegistry.getFactoryForStatus(saved.getStatus());
        OutGoingEvent outgoingEvent = factory.createEvent(syntheticInbound, saved);
        OutgoingPaymentOutbox record = outgoingPaymentOutboxService.createRecord(
                id, outgoingEvent.getEventType(), outgoingEvent.getProducer(),
                outgoingEvent.getEventId(), outgoingEvent);

        // Publish outside the strict DB write but within the request so UI gets the event fast;
        // Quartz retries cover any Kafka outage.
        try {
            outgoingPaymentPublisher.trySinglePublish(record);
        } catch (Exception e) {
            log.warn("Retry outbox publish deferred to Quartz for booking {}", id, e);
        }

        log.info("Created payment retry for booking {}: new session {} (prev FAILED {})",
                id, saved.getProviderSessionId(), latest.getProviderSessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "code", "RETRY_CREATED",
                "message", "New payment attempt created. Previous failure is not terminal; booking hold continues.",
                "bookingId", id,
                "paymentUrl", saved.getPaymentUrl(),
                "providerSessionId", saved.getProviderSessionId(),
                "status", saved.getStatus().name(),
                "retryable", true));
    }
}
