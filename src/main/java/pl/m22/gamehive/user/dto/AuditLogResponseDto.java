package pl.m22.gamehive.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Wpis dziennika audytu operacji na kontach użytkowników.")
public record AuditLogResponseDto(
        @Schema(description = "Identyfikator wpisu.", example = "1024")
        Long id,
        @Schema(description = "Rodzaj operacji.",
                example = "ROLE_CHANGE",
                allowableValues = {"ROLE_CHANGE", "DEACTIVATE", "ACTIVATE", "DELETE", "FORCE_LOGOUT", "PASSWORD_CHANGE"})
        String action,
        @Schema(description = "Identyfikator (UUID) użytkownika, którego dotyczy operacja.",
                example = "018f4e2a-7c1d-7a3b-9b2e-0a1b2c3d4e5f")
        UUID targetId,
        @Schema(description = "E-mail użytkownika, którego dotyczy operacja (wartość skopiowana w chwili zdarzenia).",
                example = "jane.smith@example.com")
        String targetEmail,
        @Schema(description = "E-mail administratora wykonującego operację (dla operacji self-service równy targetEmail).",
                example = "john.doe@example.com")
        String actor,
        @Schema(description = "Dodatkowe szczegóły (np. zmiana ról jako JSON); może być puste.",
                example = "{\"before\":[\"ROLE_USER\"],\"after\":[\"ROLE_USER\",\"ROLE_ADMIN\"]}")
        String details,
        @Schema(description = "Identyfikator korelacji powiązany z logami żądania.",
                example = "f0a1b2c3-d4e5-6789-abcd-ef0123456789")
        String correlationId,
        @Schema(description = "Czas wystąpienia operacji (UTC, ISO-8601).", example = "2026-06-20T10:15:30Z")
        Instant createdAt
) {
}