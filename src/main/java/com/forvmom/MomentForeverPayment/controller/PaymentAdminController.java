package com.forvmom.MomentForeverPayment.controller;

import com.forvmom.MomentForeverPayment.domain.entity.OutgoingPaymentOutbox;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentOutbox;
import com.forvmom.MomentForeverPayment.repository.OutgoingPaymentOutboxDao;
import com.forvmom.MomentForeverPayment.repository.PaymentOutboxDao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin/Monitoring APIs only - not for core payment flow
 */
@RestController
@RequestMapping("/admin")
public class PaymentAdminController {

    private final PaymentOutboxDao paymentOutboxDao;
    private final OutgoingPaymentOutboxDao outgoingPaymentOutboxDao;

    public PaymentAdminController(PaymentOutboxDao paymentOutboxDao,
                                  OutgoingPaymentOutboxDao outgoingPaymentOutboxDao) {
        this.paymentOutboxDao = paymentOutboxDao;
        this.outgoingPaymentOutboxDao = outgoingPaymentOutboxDao;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "payment-service");
        return ResponseEntity.ok(status);
    }

    @GetMapping("/outbox/incoming")
    public ResponseEntity<Page<PaymentOutbox>> getIncomingOutbox(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        if (status != null) {
            return ResponseEntity.ok(paymentOutboxDao.findByStatus(status, pageable));
        }
        return ResponseEntity.ok(paymentOutboxDao.findAll(pageable));
    }

    @GetMapping("/outbox/outgoing")
    public ResponseEntity<Page<OutgoingPaymentOutbox>> getOutgoingOutbox(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        if (status != null) {
            return ResponseEntity.ok(outgoingPaymentOutboxDao.findByStatus(status, pageable));
        }
        return ResponseEntity.ok(outgoingPaymentOutboxDao.findAll(pageable));
    }

    @GetMapping("/bookings/{bookingId}/status")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String bookingId) {
        Map<String, Object> response = new HashMap<>();

        // Check outgoing records to see what happened with this booking
        var processed = outgoingPaymentOutboxDao
                .findFirstByBookingIdAndEventTypeOrderByCreatedAtDesc(bookingId, "PAYMENT_PROCESSED");
        var failed = outgoingPaymentOutboxDao
                .findFirstByBookingIdAndEventTypeOrderByCreatedAtDesc(bookingId, "PAYMENT_FAILED");

        if (processed.isPresent()) {
            response.put("status", "PROCESSED");
            response.put("record", processed.get());
        } else if (failed.isPresent()) {
            response.put("status", "FAILED");
            response.put("record", failed.get());
        } else {
            response.put("status", "PENDING");
        }

        return ResponseEntity.ok(response);
    }
}