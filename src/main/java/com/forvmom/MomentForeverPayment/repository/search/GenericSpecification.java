package com.forvmom.MomentForeverPayment.repository.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;

/**
 * Generic AND-specification over {@link Filter}s.
 * Ported from booking-service {@code GenericSpecification}.
 * Prefer the entity-specific factories ({@link PaymentSearch}) over
 * calling this directly.
 */
public class GenericSpecification<T> implements Specification<T> {

    private final List<Filter> filters;

    private GenericSpecification(List<Filter> filters) {
        this.filters = filters == null ? List.of() : List.copyOf(filters);
    }

    public static <T> GenericSpecification<T> of(List<Filter> filters) {
        return new GenericSpecification<>(filters);
    }

    public static <T> GenericSpecification<T> of(Filter... filters) {
        return new GenericSpecification<>(filters == null ? List.of() : Arrays.asList(filters));
    }

    public List<Filter> filters() {
        return filters;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = SearchPredicates.toPredicates(root, cb, filters);
        if (predicates.isEmpty()) {
            return cb.conjunction();
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
