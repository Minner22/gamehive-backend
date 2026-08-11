package pl.m22.gamehive.game.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Podsumowanie przebudowy indeksu — liczba dokumentów wypchniętych z bazy.")
public record ReindexResultDto(

        @Schema(description = "Liczba zaindeksowanych zatwierdzonych gier.", example = "128",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long games,

        @Schema(description = "Liczba zaindeksowanych zatwierdzonych dodatków.", example = "37",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long expansions) {
}
