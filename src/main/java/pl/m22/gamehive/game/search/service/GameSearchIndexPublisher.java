package pl.m22.gamehive.game.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;
import pl.m22.gamehive.game.search.event.SearchIndexEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GameSearchIndexPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final GameSearchDocumentFactory documentFactory;

    public void publishUpsert(Game game) {

        publishUpsert(game, List.of());
    }

    public void publishUpsert(Game game, Collection<GameExpansion> expansions) {

        List<GameSearchDocument> documents = new ArrayList<>(expansions.size() + 1);
        documents.add(documentFactory.toDocument(game));
        expansions.forEach(expansion -> documents.add(documentFactory.toDocument(expansion)));

        eventPublisher.publishEvent(SearchIndexEvent.upsert(documents));
    }

    public void publishUpsert(GameExpansion expansion) {

        eventPublisher.publishEvent(SearchIndexEvent.upsert(documentFactory.toDocument(expansion)));
    }

    public void publishRemoval(ContentModerationTargetType targetType, Long targetId) {

        eventPublisher.publishEvent(
                SearchIndexEvent.remove(GameSearchDocumentFactory.documentId(targetType, targetId)));
    }
}
