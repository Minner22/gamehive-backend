package pl.m22.gamehive.user.dto;

import pl.m22.gamehive.user.model.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditLogFilter(UUID targetId, String actor, AuditAction action, Instant from, Instant to) {
}
