package pl.m22.gamehive.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.m22.gamehive.common.persistence.ModerationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Widok gry dla moderatora/admina — w przeciwieństwie do {@link GameDto} eksponuje pola moderacyjne
 * (submittedBy/reviewedBy/reviewedAt/resubmissionCount), których zwykły użytkownik nie widzi.
 */
@Schema(description = "Gra / zgłoszenie w widoku moderacji — z pełnymi metadanymi decyzji.")
public record GameModerationDto(

        @Schema(description = "Identyfikator gry.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(description = "Tytuł gry.", example = "Terraforming Mars", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "Opis gry.", example = "Kolonizacja Marsa w rywalizacji korporacji.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String description,

        @Schema(description = "Minimalna liczba graczy.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        int minPlayers,

        @Schema(description = "Maksymalna liczba graczy.", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        int maxPlayers,

        @Schema(description = "Czas rozgrywki w minutach.", example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
        int playingTimeMinutes,

        @Schema(description = "Rok wydania.", example = "2016", requiredMode = Schema.RequiredMode.REQUIRED)
        int yearPublished,

        @Schema(description = "Minimalny wiek gracza.", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        int minAge,

        @Schema(description = "URL okładki (opcjonalny).", example = "https://example.com/cover.jpg")
        String coverImageUrl,

        @Schema(description = "Status moderacji (DRAFT/PENDING/APPROVED/REJECTED).",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ModerationStatus moderationStatus,

        @Schema(description = "Powód odrzucenia — tylko dla statusu REJECTED, inaczej null.",
                example = "Duplikat istniejącej gry")
        String rejectionReason,

        @Schema(description = "UUID użytkownika, który zgłosił grę.", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID submittedBy,

        @Schema(description = "UUID moderatora, który podjął decyzję — null dla DRAFT/PENDING.")
        UUID reviewedBy,

        @Schema(description = "Moment decyzji moderacyjnej — null dla DRAFT/PENDING.")
        Instant reviewedAt,

        @Schema(description = "Liczba ponownych wysłań po odrzuceniu.", example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int resubmissionCount,

        @Schema(description = "Wydawcy gry.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<PublisherDto> publishers,

        @Schema(description = "Kategorie gry.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CategoryDto> categories,

        @Schema(description = "Mechaniki gry.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<MechanicDto> mechanics,

        @Schema(description = "Autorzy gry.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<AuthorDto> authors
) {
}
