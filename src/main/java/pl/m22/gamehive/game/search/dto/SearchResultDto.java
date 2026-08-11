package pl.m22.gamehive.game.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.dto.GameExpansionDto;
import pl.m22.gamehive.game.model.ContentModerationTargetType;

public record SearchResultDto(

        @Schema(description = "Typ trafienia — wskazuje, które z pól poniżej jest wypełnione", example = "GAME")
        ContentModerationTargetType targetType,

        @Schema(description = "Gra — wypełnione tylko dla targetType = GAME")
        GameDto game,

        @Schema(description = "Dodatek — wypełnione tylko dla targetType = EXPANSION")
        GameExpansionDto expansion) {

    public static SearchResultDto of(GameDto game) {

        return new SearchResultDto(ContentModerationTargetType.GAME, game, null);
    }

    public static SearchResultDto of(GameExpansionDto expansion) {

        return new SearchResultDto(ContentModerationTargetType.EXPANSION, null, expansion);
    }
}
