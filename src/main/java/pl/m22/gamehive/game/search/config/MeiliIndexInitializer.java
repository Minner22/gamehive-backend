package pl.m22.gamehive.game.search.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.game.search.service.MeiliGameSearchService;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gamehive.search.enabled", matchIfMissing = true)
public class MeiliIndexInitializer {

    private final MeiliGameSearchService searchService;

    @EventListener(ApplicationReadyEvent.class)
    void configureIndex() {

        try {
            searchService.ensureIndexSettings();
        } catch (RuntimeException e) {
            log.error("Cannot configure Meilisearch index at startup - search may return stale or no results", e);
        }
    }
}
