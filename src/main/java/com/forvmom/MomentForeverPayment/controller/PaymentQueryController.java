package com.forvmom.MomentForeverPayment.controller;

import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.dto.response.PaymentResponse;
import com.forvmom.MomentForeverPayment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Read-only payment query APIs — the payment-service counterpart of
 * booking-service {@code BookingAdminController}.
 *
 * <ul>
 *   <li>{@code GET /admin/payments/{id}} — full details of one payment</li>
 *   <li>{@code GET /admin/payments/booking/{bookingId}} — attempts for one booking</li>
 *   <li>{@code GET /admin/payments} — paginated list of all payments</li>
 *   <li>{@code GET /admin/payments/filter} — filtered, paginated search</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/payments")
@Tag(name = "Payment Query", description = "Read-only payment detail, list and filter APIs")
public class PaymentQueryController {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "createdAt", "updatedAt", "amount", "status", "provider", "bookingId");
    private static final String DEFAULT_SORT_FIELD = "createdAt";
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentQueryService paymentQueryService;

    public PaymentQueryController(PaymentQueryService paymentQueryService) {
        this.paymentQueryService = paymentQueryService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Payment Details",
            description = "Full details of a single payment by its primary key.")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentQueryService.getPaymentById(id));
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get Payments by Booking ID",
            description = "Every payment attempt for one booking, newest first "
                    + "(retries create new rows; the latest row is the current attempt).")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByBookingId(
            @PathVariable String bookingId) {
        return ResponseEntity.ok(paymentQueryService.getPaymentsByBookingId(bookingId));
    }

    @GetMapping
    @Operation(summary = "List All Payments",
            description = "Paginated list of all payments, newest first. "
                    + "sort takes the form '<field>,<asc|desc>' (default 'createdAt,desc'); allowed fields: "
                    + "createdAt, updatedAt, amount, status, provider, bookingId.")
    public ResponseEntity<Page<PaymentResponse>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        PageRequest pageRequest = PageRequest.of(
                validatePage(page), validateSize(size), parseSort(sort));
        return ResponseEntity.ok(paymentQueryService.getAllPayments(pageRequest));
    }

    @GetMapping("/filter")
    @Operation(summary = "Filter Payments",
            description = "Paginated, filterable, sortable payment search. "
                    + "Every filter is optional (absent = no filter). "
                    + "from/to bound createdAt; minAmount/maxAmount bound amount.")
    public ResponseEntity<Page<PaymentResponse>> filterPayments(
            @RequestParam(required = false) List<PaymentStatus> status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String bookingId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        validateRange(from, to);
        validateAmountRange(minAmount, maxAmount);
        PageRequest pageRequest = PageRequest.of(
                validatePage(page), validateSize(size), parseSort(sort));
        Page<PaymentResponse> result = paymentQueryService.searchPayments(
                status, provider, bookingId, currency, from, to, minAmount, maxAmount, pageRequest);
        return ResponseEntity.ok(result);
    }

    // ─── Query-param validation helpers (same shape as booking-service) ──

    private Sort parseSort(String sort) {
        return PaymentQuerySupport.parseSort(sort, SORTABLE_FIELDS, DEFAULT_SORT_FIELD);
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        PaymentQuerySupport.validateRange(from, to);
    }

    private void validateAmountRange(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                    "'minAmount' (" + min + ") must not be greater than 'maxAmount' (" + max + ")");
        }
        if (min != null && min.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("'minAmount' must not be negative");
        }
    }

    private int validatePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("'page' must not be negative");
        }
        return page;
    }

    private int validateSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "'size' must be between 1 and " + MAX_PAGE_SIZE);
        }
        return size;
    }
}
