package pl.m22.gamehive.game.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Podsumowanie przebudowy indeksów — liczba dokumentów wypchniętych z bazy.")
public record ReindexResultDto(

        @Schema(description = "Liczba zaindeksowanych zatwierdzonych gier.", example = "128",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long games,

        @Schema(description = "Liczba zaindeksowanych zatwierdzonych dodatków.", example = "37",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long expansions,

        @Schema(description = "Liczba zaindeksowanych wydawców (wszystkie statusy, nie tylko APPROVED).",
                example = "412", requiredMode = Schema.RequiredMode.REQUIRED)
        long publishers,

        @Schema(description = "Liczba zaindeksowanych autorów (wszystkie statusy, nie tylko APPROVED).",
                example = "1180", requiredMode = Schema.RequiredMode.REQUIRED)
        long authors) {
}
