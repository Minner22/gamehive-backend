package pl.m22.gamehive.user.service;

import org.springframework.data.jpa.domain.Specification;
import pl.m22.gamehive.user.dto.AuditLogFilter;
import pl.m22.gamehive.user.model.UserAuditLogEntry;

import java.time.Instant;

public final class UserAuditLogSpecifications {

    private UserAuditLogSpecifications() {
        throw new IllegalStateException("Utility class");
    }

    public static Specification<UserAuditLogEntry> withFilter(AuditLogFilter filter) {

        return Specification.allOf(
                equalsIfPresent("targetId", filter.targetId()),
                equalsIfPresent("actor", filter.actor()),
                equalsIfPresent("action", filter.action()),
                createdFrom(filter.from()),
                createdTo(filter.to())
        );
    }

    // predykat null -> Specification.allOf pomija ten warunek (filtr nieobecny)
    private static Specification<UserAuditLogEntry> equalsIfPresent(String attribute, Object value) {

        return (root, query, cb) -> value == null
                ? null
                : cb.equal(root.get(attribute), value);
    }

    private static Specification<UserAuditLogEntry> createdFrom(Instant from) {

        return (root, query, cb) -> from == null
                ? null
                : cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from);
    }

    private static Specification<UserAuditLogEntry> createdTo(Instant to) {

        return (root, query, cb) -> to == null
                ? null
                : cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), to);
    }
}
