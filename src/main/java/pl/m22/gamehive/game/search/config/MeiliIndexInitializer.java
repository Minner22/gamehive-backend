package pl.m22.gamehive.game.search.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.game.search.service.MeiliGameSearchService;
import pl.m22.gamehive.game.search.service.MeiliTaxonomySuggestService;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gamehive.search.enabled", matchIfMissing = true)
public class MeiliIndexInitializer {

    private final MeiliGameSearchService searchService;
    private final MeiliTaxonomySuggestService taxonomySuggestService;

    @EventListener(ApplicationReadyEvent.class)
    void configureIndexes() {

        configure(searchService::ensureIndexSettings, "content");
        configure(taxonomySuggestService::ensureIndexSettings, "taxonomy");
    }

    private static void configure(Runnable settings, String index) {

        try {
            settings.run();
        } catch (RuntimeException e) {
            log.error("Cannot configure Meilisearch {} index at startup - search may return stale or no results",
                    index, e);
        }
    }
}
