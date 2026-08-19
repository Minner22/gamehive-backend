package pl.m22.gamehive.game.search.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.config.AsyncConfig;
import pl.m22.gamehive.game.search.service.TaxonomySuggestService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaxonomyIndexListener {

    private final TaxonomySuggestService taxonomySuggestService;

    @Async(AsyncConfig.SEARCH_INDEX_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaxonomyIndex(TaxonomyIndexEvent event) {

        try {
            switch (event.operation()) {
                case UPSERT -> taxonomySuggestService.index(event.documents());
                case REMOVE -> taxonomySuggestService.delete(event.documentId());
            }
        } catch (RuntimeException e) {
            log.error("Taxonomy index update failed for {} ({}) - suggestions are out of sync until a reindex",
                    event.describeTargets(), event.operation(), e);
        }
    }
}
