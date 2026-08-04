package pl.m22.gamehive.common.persistence;

import org.springframework.data.jpa.domain.Specification;

public final class Specifications {

    private Specifications() {
        throw new IllegalStateException("Utility class");
    }

    public static <T> Specification<T> equalsIfPresent(String attribute, Object value) {

        return (root, query, cb) -> value == null ? null : cb.equal(root.get(attribute), value);
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> lessThanOrEqualToIfPresent(String attribute, Y value) {

        return (root, query, cb) -> value == null ? null : cb.lessThanOrEqualTo(root.<Y>get(attribute), value);
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> greaterThanOrEqualToIfPresent(String attribute, Y value) {

        return (root, query, cb) -> value == null ? null : cb.greaterThanOrEqualTo(root.<Y>get(attribute), value);
    }
}