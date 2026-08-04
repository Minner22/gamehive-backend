package pl.m22.gamehive.user.service;

import org.springframework.data.jpa.domain.Specification;
import pl.m22.gamehive.common.persistence.Specifications;
import pl.m22.gamehive.user.dto.AuditLogFilter;
import pl.m22.gamehive.user.model.UserAuditLogEntry;

import java.time.Instant;

public final class UserAuditLogSpecifications {

    private UserAuditLogSpecifications() {
        throw new IllegalStateException("Utility class");
    }
    
    public static Specification<UserAuditLogEntry> withFilter(AuditLogFilter filter) {

        return Specification.allOf(
                Specifications.<UserAuditLogEntry>equalsIfPresent("targetId", filter.targetId()),
                Specifications.<UserAuditLogEntry>equalsIfPresent("actor", filter.actor()),
                Specifications.<UserAuditLogEntry>equalsIfPresent("action", filter.action()),
                Specifications.<UserAuditLogEntry, Instant>greaterThanOrEqualToIfPresent("createdAt", filter.from()),
                Specifications.<UserAuditLogEntry, Instant>lessThanOrEqualToIfPresent("createdAt", filter.to())
        );
    }
}