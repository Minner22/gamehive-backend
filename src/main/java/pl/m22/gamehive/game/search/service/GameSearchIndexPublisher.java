package pl.m22.gamehive.game.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.search.event.SearchIndexEvent;

@Component
@RequiredArgsConstructor
public class GameSearchIndexPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final GameSearchDocumentFactory documentFactory;

    public void publishUpsert(Game game) {

        eventPublisher.publishEvent(SearchIndexEvent.upsert(documentFactory.toDocument(game)));
    }

    public void publishUpsert(GameExpansion expansion) {

        eventPublisher.publishEvent(SearchIndexEvent.upsert(documentFactory.toDocument(expansion)));
    }

    public void publishRemoval(ContentModerationTargetType targetType, Long targetId) {

        eventPublisher.publishEvent(
                SearchIndexEvent.remove(GameSearchDocumentFactory.documentId(targetType, targetId)));
    }
}
