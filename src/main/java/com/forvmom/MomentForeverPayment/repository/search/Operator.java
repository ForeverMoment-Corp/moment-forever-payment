package com.forvmom.MomentForeverPayment.repository.search;

/**
 * Query vocabulary for the generic payment search engine.
 * Ported from booking-service {@code Operator} — entity-agnostic.
 */
public enum Operator {
    EQ,
    IN,
    LIKE,
    GTE,
    LTE,
    GT,
    LT,
    BETWEEN,
    IS_NULL,
    IS_NOT_NULL
}
