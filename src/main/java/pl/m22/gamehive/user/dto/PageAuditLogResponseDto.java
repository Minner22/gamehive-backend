package pl.m22.gamehive.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Schemat dokumentacyjny stronicowanej odpowiedzi z wpisami audytu.
 * Odwzorowuje płaski JSON {@code PageImpl} z wymaganymi polami — nie jest serializowany,
 * służy wyłącznie do wygenerowania kontraktu OpenAPI (zastępuje słaby auto-schemat {@code Page<AuditLogResponseDto>}).
 */
@Schema(description = "Stronicowana lista wpisów audytu.")
public record PageAuditLogResponseDto(
        @Schema(description = "Zawartość bieżącej strony.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<AuditLogResponseDto> content,
        @Schema(description = "Numer bieżącej strony (od 0).", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int number,
        @Schema(description = "Rozmiar strony.", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(description = "Łączna liczba elementów.", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(description = "Łączna liczba stron.", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages,
        @Schema(description = "Czy to pierwsza strona.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean first,
        @Schema(description = "Czy to ostatnia strona.", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean last,
        @Schema(description = "Liczba elementów na bieżącej stronie.", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
        int numberOfElements,
        @Schema(description = "Czy strona jest pusta.", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean empty
) {
}
