package pl.m22.gamehive.game.search.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.config.AsyncConfig;
import pl.m22.gamehive.game.search.service.GameSearchService;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexListener {

    private final GameSearchService gameSearchService;

    @Async(AsyncConfig.SEARCH_INDEX_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSearchIndex(SearchIndexEvent event) {

        try {
            switch (event.operation()) {
                case UPSERT -> gameSearchService.index(event.documents());
                case REMOVE -> gameSearchService.delete(event.documentId());
            }
        } catch (RuntimeException e) {
            log.error("Search index update failed for {} ({}) - index is now out of sync until a reindex",
                    event.describeTargets(), event.operation(), e);
        }
    }
}
