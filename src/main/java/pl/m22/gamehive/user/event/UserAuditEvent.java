package pl.m22.gamehive.user.event;

import pl.m22.gamehive.user.model.AuditAction;

import java.util.UUID;

public record UserAuditEvent(AuditAction action, UUID targetId, String targetEmail, String actor, String details, String correlationId) {
}
