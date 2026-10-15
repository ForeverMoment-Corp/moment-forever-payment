package com.forvmom.MomentForeverPayment.service;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import com.forvmom.MomentForeverPayment.dto.response.PaymentResponse;
import com.forvmom.MomentForeverPayment.exception.PaymentNotFoundException;
import com.forvmom.MomentForeverPayment.mapper.PaymentMapper;
import com.forvmom.MomentForeverPayment.repository.PaymentRepository;
import com.forvmom.MomentForeverPayment.repository.search.PaymentSearch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Read-only query facade for payment details.
 *
 * <p>Returns response DTOs, never entities: DTOs are mapped inside the
 * service transaction and carry no persistence state, so Jackson can
 * serialize them after the session closes (same rule as booking-service).
 */
@Service
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    public PaymentQueryService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /** Existing UI poll path — kept for {@code PaymentController}. */
    @Transactional(readOnly = true)
    public Optional<Payment> getLatestPaymentForBooking(String bookingId) {
        return paymentRepository.findFirstByBookingIdOrderByIdDesc(normalize(bookingId));
    }

    /** API 1 — full details of a single payment by its primary key. */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Payment id is required");
        }
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + id, String.valueOf(id)));
        return PaymentMapper.toResponse(payment);
    }

    /** API 2 — every payment attempt for one booking, newest first. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByBookingId(String bookingId) {
        String id = requireBookingId(bookingId);
        List<Payment> payments = paymentRepository.findAllByBookingIdOrderByIdDesc(id);
        if (payments.isEmpty()) {
            throw new PaymentNotFoundException("No payments found for booking: " + id, id);
        }
        return payments.stream().map(PaymentMapper::toResponse).toList();
    }

    /** API 2 (paged variant) — same data, pageable for bookings with many retries. */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByBookingId(String bookingId, Pageable pageable) {
        String id = requireBookingId(bookingId);
        Page<Payment> page = paymentRepository.findAll(
                PaymentSearch.byBookingId(id), pageable);
        if (page.isEmpty()) {
            throw new PaymentNotFoundException("No payments found for booking: " + id, id);
        }
        return page.map(PaymentMapper::toResponse);
    }

    /** API 3 — paginated list of all payments (no filters). */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(PaymentMapper::toResponse);
    }

    /**
     * API 4 — filtered, paginated, sortable payment search.
     * Every filter is optional (null/empty = no filter for that field).
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> searchPayments(
            List<PaymentStatus> statuses,
            String provider,
            String bookingId,
            String currency,
            LocalDateTime from,
            LocalDateTime to,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Pageable pageable) {
        Page<Payment> page = paymentRepository.findAll(
                PaymentSearch.adminGrid(statuses, provider, bookingId, currency,
                        from, to, minAmount, maxAmount),
                pageable);
        return page.map(PaymentMapper::toResponse);
    }

    private static String requireBookingId(String bookingId) {
        String id = normalize(bookingId);
        if (id == null) {
            throw new IllegalArgumentException("bookingId must not be blank");
        }
        return id;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
