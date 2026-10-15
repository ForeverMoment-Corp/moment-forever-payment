package com.forvmom.MomentForeverPayment.repository.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates generic {@link Filter}s into JPA Criteria {@link Predicate}s.
 * Ported from booking-service {@code SearchPredicates} — entity-agnostic.
 */
public final class SearchPredicates {

    private SearchPredicates() {
        // Static helpers only.
    }

    public static List<Predicate> toPredicates(Root<?> root, CriteriaBuilder cb, List<Filter> filters) {
        List<Predicate> predicates = new ArrayList<>();
        if (filters == null) {
            return predicates;
        }
        for (Filter filter : filters) {
            if (filter != null) {
                predicates.add(toPredicate(root, cb, filter));
            }
        }
        return predicates;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Predicate toPredicate(Root<?> root, CriteriaBuilder cb, Filter filter) {
        String field = filter.field();
        switch (filter.operator()) {
            case EQ:
                if (filter.value() == null) {
                    return cb.conjunction();
                }
                return cb.equal(root.get(field), filter.value());
            case IN:
                if (filter.values() == null || filter.values().isEmpty()) {
                    return cb.conjunction();
                }
                return root.get(field).in(filter.values());
            case LIKE:
                if (filter.value() == null || filter.value().toString().isBlank()) {
                    return cb.conjunction();
                }
                return cb.like(
                        cb.lower(root.get(field).as(String.class)),
                        "%" + filter.value().toString().toLowerCase() + "%");
            case GTE:
                if (filter.value() == null) {
                    return cb.conjunction();
                }
                return cb.greaterThanOrEqualTo(root.get(field), (Comparable) filter.value());
            case LTE:
                if (filter.value() == null) {
                    return cb.conjunction();
                }
                return cb.lessThanOrEqualTo(root.get(field), (Comparable) filter.value());
            case GT:
                if (filter.value() == null) {
                    return cb.conjunction();
                }
                return cb.greaterThan(root.get(field), (Comparable) filter.value());
            case LT:
                if (filter.value() == null) {
                    return cb.conjunction();
                }
                return cb.lessThan(root.get(field), (Comparable) filter.value());
            case BETWEEN: {
                Comparable from = (Comparable) filter.value();
                Comparable to = (Comparable) filter.secondValue();
                if (from == null && to == null) {
                    return cb.conjunction();
                }
                if (from == null) {
                    return cb.lessThanOrEqualTo(root.get(field), to);
                }
                if (to == null) {
                    return cb.greaterThanOrEqualTo(root.get(field), from);
                }
                return cb.between(root.get(field), from, to);
            }
            case IS_NULL:
                return cb.isNull(root.get(field));
            case IS_NOT_NULL:
                return cb.isNotNull(root.get(field));
            default:
                throw new IllegalArgumentException("Unsupported operator: " + filter.operator());
        }
    }
}
