package pl.m22.gamehive.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

@Schema(description = "Żądanie utworzenia (POST) lub edycji (PUT) zgłoszenia gry. "
        + "Wydawców i autorów można wskazać po id i/lub podać nowych do utworzenia w locie (można mieszać).")
public record GameRequestDto(

        @Schema(description = "Tytuł gry.", example = "Terraforming Mars", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String title,

        @Schema(description = "Opis gry.", example = "Kolonizacja Marsa w rywalizacji korporacji.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String description,

        @Schema(description = "Minimalna liczba graczy.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) Integer minPlayers,

        @Schema(description = "Maksymalna liczba graczy (>= minPlayers).", example = "5",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) Integer maxPlayers,

        @Schema(description = "Czas rozgrywki w minutach.", example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) Integer playingTimeMinutes,

        @Schema(description = "Rok wydania.", example = "2016", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1900) Integer yearPublished,

        @Schema(description = "Minimalny wiek gracza.", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(0) @Max(21) Integer minAge,

        @Schema(description = "URL okładki (opcjonalny).", example = "https://example.com/cover.jpg")
        @Size(max = 512) String coverImageUrl,

        @Schema(description = "Id istniejących wydawców. Razem z newPublisherNames musi dać >= 1 wydawcę.")
        List<Long> publisherIds,

        @Schema(description = "Nazwy nowych wydawców — utworzeni w locie ze statusem PENDING "
                + "(istniejąca nazwa = reuse istniejącego wpisu).")
        List<@NotBlank String> newPublisherNames,

        @Schema(description = "Id kategorii (>= 1; kategorie są kuratorowane, nietworzone w locie).")
        List<Long> categoryIds,

        @Schema(description = "Id mechanik (opcjonalne; mechaniki są kuratorowane, nietworzone w locie).")
        List<Long> mechanicIds,

        @Schema(description = "Id istniejących autorów (opcjonalni).")
        List<Long> authorIds,

        @Schema(description = "Nowi autorzy — find-or-create po parze imię+nazwisko "
                + "(nowa para = PENDING, istniejąca = reuse bez zmiany statusu).")
        List<@Valid AuthorRequestDto> newAuthors,

        @Schema(description = "true = od razu wyślij do moderacji (PENDING), false = zapisz szkic (DRAFT). "
                + "Używane tylko przy POST; PUT ignoruje to pole — status zmienia wyłącznie POST /{id}/submit.")
        boolean submit
) {
}
