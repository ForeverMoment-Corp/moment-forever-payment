package com.forvmom.MomentForeverPayment.controller;

import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Shared query-param handling for the payment read endpoints.
 * Mirrors booking-service {@code BookingQuerySupport}: both list endpoints
 * reject unknown sort fields and inverted ranges with clear 400s instead of
 * opaque 500s from Spring Data.
 */
final class PaymentQuerySupport {

    private PaymentQuerySupport() {
        // Static helpers only.
    }

    static Sort parseSort(String sort, Set<String> allowedFields, String defaultField) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, defaultField);
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!allowedFields.contains(field)) {
            throw new IllegalArgumentException(
                    "Invalid sort field '" + field + "'. Allowed values: " + allowedFields);
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            String rawDirection = parts[1].trim();
            try {
                direction = Sort.Direction.fromString(rawDirection);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Invalid sort direction '" + rawDirection + "'. Allowed values: asc, desc");
            }
        }
        return Sort.by(direction, field);
    }

    static void validateRange(LocalDateTime from, LocalDateTime to, String fromName, String toName) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "'" + fromName + "' (" + from + ") must not be after '" + toName + "' (" + to + ")");
        }
    }

    static void validateRange(LocalDateTime from, LocalDateTime to) {
        validateRange(from, to, "from", "to");
    }
}
