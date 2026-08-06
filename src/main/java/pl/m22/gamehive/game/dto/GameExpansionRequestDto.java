package pl.m22.gamehive.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.List;

@Schema(description = "Żądanie utworzenia (POST) lub edycji (PUT) zgłoszenia dodatku. "
        + "Pola liczbowe i kolekcje są opcjonalnymi nadpisaniami gry bazowej — puste oznacza dziedziczenie.")
public record GameExpansionRequestDto(

        @Schema(description = "Id gry bazowej (musi być APPROVED). Używane tylko przy POST; "
                + "PUT ignoruje to pole — dodatku nie da się przenieść na inną grę.",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long baseGameId,

        @Schema(description = "Nazwa dodatku.", example = "Terraforming Mars: Preludium",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String name,

        @Schema(description = "Opis dodatku.", example = "Przyspiesza start rozgrywki.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String description,

        @Schema(description = "Nadpisanie minimalnej liczby graczy (puste = jak w grze bazowej).", example = "2")
        @Min(1) Integer minPlayers,

        @Schema(description = "Nadpisanie maksymalnej liczby graczy (puste = jak w grze bazowej).", example = "6")
        @Min(1) Integer maxPlayers,

        @Schema(description = "Nadpisanie czasu rozgrywki w minutach (puste = jak w grze bazowej).", example = "150")
        @Min(1) Integer playingTimeMinutes,

        @Schema(description = "Nadpisanie minimalnego wieku gracza (puste = jak w grze bazowej).", example = "14")
        @Min(0) @Max(21) Integer minAge,

        @Schema(description = "Id własnych kategorii dodatku (puste = dziedziczy kategorie gry bazowej).")
        List<Long> categoryIds,

        @Schema(description = "Id własnych mechanik dodatku (puste = dziedziczy mechaniki gry bazowej).")
        List<Long> mechanicIds,

        @Schema(description = "true = od razu wyślij do moderacji (PENDING), false = zapisz szkic (DRAFT). "
                + "Używane tylko przy POST; PUT ignoruje to pole — status zmienia wyłącznie POST /{id}/submit.")
        boolean submit
) {
}
