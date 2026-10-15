package com.forvmom.MomentForeverPayment.repository.search;

import java.util.Collection;
import java.util.List;

/**
 * One declarative filter: field + comparison + value(s).
 * Ported from booking-service {@code Filter} — entity-agnostic.
 * Absent values (null / empty / blank) render as no-ops downstream,
 * so PostgreSQL never receives an untyped null bind (42P18).
 */
public record Filter(String field, Operator operator, Object value, Object secondValue, List<?> values) {

    public static Filter eq(String field, Object value) {
        return new Filter(field, Operator.EQ, value, null, null);
    }

    public static Filter in(String field, Collection<?> values) {
        return new Filter(field, Operator.IN, null, null,
                values == null ? null : List.copyOf(values));
    }

    public static Filter like(String field, String text) {
        return new Filter(field, Operator.LIKE, text, null, null);
    }

    public static Filter gte(String field, Comparable<?> value) {
        return new Filter(field, Operator.GTE, value, null, null);
    }

    public static Filter lte(String field, Comparable<?> value) {
        return new Filter(field, Operator.LTE, value, null, null);
    }

    public static Filter gt(String field, Comparable<?> value) {
        return new Filter(field, Operator.GT, value, null, null);
    }

    public static Filter lt(String field, Comparable<?> value) {
        return new Filter(field, Operator.LT, value, null, null);
    }

    public static Filter between(String field, Comparable<?> from, Comparable<?> to) {
        return new Filter(field, Operator.BETWEEN, from, to, null);
    }

    public static Filter isNull(String field) {
        return new Filter(field, Operator.IS_NULL, null, null, null);
    }

    public static Filter isNotNull(String field) {
        return new Filter(field, Operator.IS_NOT_NULL, null, null, null);
    }
}
