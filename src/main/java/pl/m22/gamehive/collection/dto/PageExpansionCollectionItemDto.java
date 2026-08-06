package pl.m22.gamehive.collection.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Schemat dokumentacyjny stronicowanej kolekcji dodatków. Odwzorowuje płaski JSON {@code PageImpl} —
 * nie jest serializowany, zastępuje słaby auto-schemat {@code Page<ExpansionCollectionItemDto>}.
 */
@Schema(description = "Stronicowana lista dodatków w kolekcji.")
public record PageExpansionCollectionItemDto(

        @Schema(description = "Zawartość bieżącej strony.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ExpansionCollectionItemDto> content,
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
