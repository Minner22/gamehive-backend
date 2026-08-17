package pl.m22.gamehive.common.persistence;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class Specifications {

    public static final char LIKE_ESCAPE = '\\';

    private Specifications() {
        throw new IllegalStateException("Utility class");
    }

    public static <T> Specification<T> joinEqualsIfPresent(String association, Object value) {

        return (root, query, cb) -> {
            if (value == null) {
                return null;
            }
            if (query != null) {
                query.distinct(true);
            }
            return cb.equal(root.join(association, JoinType.INNER).get("id"), value);
        };
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

    public static <T> Specification<T> likeIgnoreCaseIfPresent(String attribute, String value) {

        return (root, query, cb) -> isBlank(value)
                ? null
                : cb.like(cb.lower(root.get(attribute)), likePattern(value), LIKE_ESCAPE);
    }

    public static String likePattern(String value) {

        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        return "%" + escaped + "%";
    }

    public static boolean isBlank(String value) {

        return value == null || value.isBlank();
    }
}