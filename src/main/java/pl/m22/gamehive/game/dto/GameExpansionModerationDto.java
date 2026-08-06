package pl.m22.gamehive.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.m22.gamehive.common.persistence.ModerationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Widok dodatku dla moderatora/admina — w przeciwieństwie do {@link GameExpansionDto} eksponuje pola
 * moderacyjne (submittedBy/reviewedBy/reviewedAt/resubmissionCount), których zwykły użytkownik nie widzi.
 */
@Schema(description = "Dodatek / zgłoszenie dodatku w widoku moderacji — z pełnymi metadanymi decyzji.")
public record GameExpansionModerationDto(

        @Schema(description = "Identyfikator dodatku.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(description = "Identyfikator gry bazowej.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long baseGameId,

        @Schema(description = "Tytuł gry bazowej.", example = "Terraforming Mars",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String baseGameTitle,

        @Schema(description = "Nazwa dodatku.", example = "Terraforming Mars: Preludium",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Opis dodatku.", example = "Przyspiesza start rozgrywki.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String description,

        @Schema(description = "Własne minimum graczy — null oznacza dziedziczenie z gry bazowej.", example = "2")
        Integer minPlayers,

        @Schema(description = "Własne maksimum graczy — null oznacza dziedziczenie z gry bazowej.", example = "6")
        Integer maxPlayers,

        @Schema(description = "Własny czas rozgrywki — null oznacza dziedziczenie z gry bazowej.", example = "150")
        Integer playingTimeMinutes,

        @Schema(description = "Własny minimalny wiek — null oznacza dziedziczenie z gry bazowej.", example = "14")
        Integer minAge,

        @Schema(description = "Minimum graczy po uwzględnieniu dziedziczenia.", example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int effectiveMinPlayers,

        @Schema(description = "Maksimum graczy po uwzględnieniu dziedziczenia.", example = "6",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int effectiveMaxPlayers,

        @Schema(description = "Czas rozgrywki po uwzględnieniu dziedziczenia.", example = "150",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int effectivePlayingTimeMinutes,

        @Schema(description = "Minimalny wiek po uwzględnieniu dziedziczenia.", example = "14",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int effectiveMinAge,

        @Schema(description = "Własne kategorie dodatku — pusta lista oznacza dziedziczenie.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<CategoryDto> categories,

        @Schema(description = "Własne mechaniki dodatku — pusta lista oznacza dziedziczenie.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<MechanicDto> mechanics,

        @Schema(description = "Kategorie po uwzględnieniu dziedziczenia.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CategoryDto> effectiveCategories,

        @Schema(description = "Mechaniki po uwzględnieniu dziedziczenia.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<MechanicDto> effectiveMechanics,

        @Schema(description = "Status moderacji (DRAFT/PENDING/APPROVED/REJECTED).",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ModerationStatus moderationStatus,

        @Schema(description = "Powód odrzucenia — tylko dla statusu REJECTED, inaczej null.",
                example = "Opis nie odróżnia dodatku od bazy")
        String rejectionReason,

        @Schema(description = "UUID użytkownika, który zgłosił dodatek.", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID submittedBy,

        @Schema(description = "UUID moderatora, który podjął decyzję — null dla DRAFT/PENDING.")
        UUID reviewedBy,

        @Schema(description = "Moment decyzji moderacyjnej — null dla DRAFT/PENDING.")
        Instant reviewedAt,

        @Schema(description = "Liczba ponownych wysłań po odrzuceniu.", example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int resubmissionCount
) {
}
