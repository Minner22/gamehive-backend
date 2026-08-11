package pl.m22.gamehive.game.search.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.game.search.service.GameSearchService;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexListener {

    private final GameSearchService gameSearchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSearchIndex(SearchIndexEvent event) {

        try {
            switch (event.operation()) {
                case UPSERT -> gameSearchService.index(event.document());
                case REMOVE -> gameSearchService.delete(event.documentId());
            }
        } catch (RuntimeException e) {
            log.error("Search index update failed for {} ({}) - index is now out of sync until a reindex",
                    event.documentId(), event.operation(), e);
        }
    }
}
