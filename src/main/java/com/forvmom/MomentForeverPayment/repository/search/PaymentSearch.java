package com.forvmom.MomentForeverPayment.repository.search;

import com.forvmom.MomentForeverPayment.domain.entity.Payment;
import com.forvmom.MomentForeverPayment.domain.entity.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity-specific search rules for {@link Payment}.
 *
 * <p>The <i>only</i> place that decides which filters a payment search
 * supports and which entity attributes they map to — same layered pattern
 * as booking-service {@code BookingSearch}. Filters reference entity
 * attribute names (e.g. {@code "createdAt"}), not DB columns.
 */
public final class PaymentSearch {

    public static final String STATUS = "status";
    public static final String PROVIDER = "provider";
    public static final String BOOKING_ID = "bookingId";
    public static final String CURRENCY = "currency";
    public static final String AMOUNT = "amount";
    public static final String CREATED_AT = "createdAt";
    public static final String PROVIDER_SESSION_ID = "providerSessionId";
    public static final String TRANSACTION_ID = "transactionId";

    private PaymentSearch() {
        // Static factories only.
    }

    /**
     * Admin grid filter: every parameter optional (null/empty = no filter).
     * {@code from}/{@code to} bound {@code createdAt};
     * {@code minAmount}/{@code maxAmount} bound {@code amount}.
     */
    public static Specification<Payment> adminGrid(
            List<PaymentStatus> statuses,
            String provider,
            String bookingId,
            String currency,
            LocalDateTime from,
            LocalDateTime to,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        return GenericSpecification.of(
                Filter.in(STATUS, statuses),
                Filter.eq(PROVIDER, normalize(provider)),
                Filter.eq(BOOKING_ID, normalize(bookingId)),
                Filter.eq(CURRENCY, normalize(currency)),
                Filter.gte(CREATED_AT, from),
                Filter.lte(CREATED_AT, to),
                Filter.gte(AMOUNT, minAmount),
                Filter.lte(AMOUNT, maxAmount));
    }

    /** All payment attempts for one booking. */
    public static Specification<Payment> byBookingId(String bookingId) {
        return GenericSpecification.of(Filter.eq(BOOKING_ID, normalize(bookingId)));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
