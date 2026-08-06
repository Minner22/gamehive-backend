package pl.m22.gamehive.collection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.m22.gamehive.collection.model.OwnershipStatus;
import pl.m22.gamehive.game.dto.GameExpansionDto;

import java.time.Instant;

/**
 * Wpis kolekcji z zagnieżdżonym {@link GameExpansionDto} — niesie także wartości efektywne dodatku,
 * więc front nie musi dociągać gry bazowej.
 */
@Schema(description = "Dodatek w prywatnej kolekcji użytkownika.")
public record ExpansionCollectionItemDto(

        @Schema(description = "Identyfikator wpisu kolekcji.", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(description = "Status posiadania — w MVP zawsze OWNED.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OwnershipStatus ownershipStatus,

        @Schema(description = "Moment dodania do kolekcji.", example = "2026-08-06T12:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant addedAt,

        @Schema(description = "Dane dodatku z biblioteki.", requiredMode = Schema.RequiredMode.REQUIRED)
        GameExpansionDto expansion
) {
}
