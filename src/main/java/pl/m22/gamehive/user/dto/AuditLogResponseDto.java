package pl.m22.gamehive.user.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponseDto(
        Long id,
        String action,
        UUID targetId,
        String targetEmail,
        String actor,
        String details,
        String correlationId,
        Instant createdAt
) {
}
