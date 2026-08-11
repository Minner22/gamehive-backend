package pl.m22.gamehive.game.search.service;

import org.springframework.stereotype.Component;
import pl.m22.gamehive.common.persistence.LongEntity;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;

import java.util.Collection;
import java.util.List;

@Component
public class GameSearchDocumentFactory {

    public GameSearchDocument toDocument(Game game) {

        return new GameSearchDocument(
                documentId(ContentModerationTargetType.GAME, game.getId()),
                ContentModerationTargetType.GAME,
                game.getId(),
                game.getTitle(),
                game.getDescription(),
                null,
                ids(game.getPublishers()),
                ids(game.getCategories()),
                ids(game.getMechanics()),
                ids(game.getAuthors()),
                game.getMinPlayers(),
                game.getMaxPlayers(),
                game.getPlayingTimeMinutes(),
                game.getYearPublished(),
                game.getMinAge(),
                null);
    }

    public GameSearchDocument toDocument(GameExpansion expansion) {

        return new GameSearchDocument(
                documentId(ContentModerationTargetType.EXPANSION, expansion.getId()),
                ContentModerationTargetType.EXPANSION,
                expansion.getId(),
                expansion.getName(),
                expansion.getDescription(),
                expansion.getBaseGame().getTitle(),
                List.of(),
                ids(expansion.getEffectiveCategories()),
                ids(expansion.getEffectiveMechanics()),
                List.of(),
                expansion.getEffectiveMinPlayers(),
                expansion.getEffectiveMaxPlayers(),
                expansion.getEffectivePlayingTimeMinutes(),
                null,
                expansion.getEffectiveMinAge(),
                expansion.getBaseGame().getId());
    }

    public static String documentId(ContentModerationTargetType targetType, Long targetId) {

        return switch (targetType) {
            case GAME -> "game-" + targetId;
            case EXPANSION -> "expansion-" + targetId;
        };
    }

    private static List<Long> ids(Collection<? extends LongEntity> entities) {

        return entities.stream().map(LongEntity::getId).toList();
    }
}
