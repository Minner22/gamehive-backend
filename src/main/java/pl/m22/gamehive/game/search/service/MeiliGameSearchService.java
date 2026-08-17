package pl.m22.gamehive.game.search.service;

import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.SearchResultPaginated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.config.MeiliClientConfig;
import pl.m22.gamehive.game.search.config.MeiliProperties;
import pl.m22.gamehive.game.search.dto.*;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "gamehive.search.enabled", matchIfMissing = true)
public class MeiliGameSearchService implements GameSearchService {

    static final String[] SEARCHABLE_ATTRIBUTES = {"title", "description", "baseGameTitle"};

    static final String[] FILTERABLE_ATTRIBUTES = {"targetType", "publisherIds", "categoryIds", "mechanicIds",
            "authorIds", "baseGameId", "minPlayers", "maxPlayers", "playingTimeMinutes", "yearPublished", "minAge"};

    private final MeiliIndexGateway gateway;
    private final MeiliFilterBuilder filterBuilder;
    private final SearchResultHydrator hydrator;
    private final ApprovedContentDocumentReader documentReader;
    private final int reindexBatchSize;

    public MeiliGameSearchService(@Qualifier(MeiliClientConfig.CONTENT_GATEWAY) MeiliIndexGateway gateway,
                                  MeiliFilterBuilder filterBuilder,
                                  SearchResultHydrator hydrator,
                                  ApprovedContentDocumentReader documentReader,
                                  MeiliProperties properties) {

        this.gateway = gateway;
        this.filterBuilder = filterBuilder;
        this.hydrator = hydrator;
        this.documentReader = documentReader;
        this.reindexBatchSize = properties.getReindexBatchSize();
    }

    @Override
    public void index(List<GameSearchDocument> documents) {

        if (documents.isEmpty()) {
            return;
        }

        String action = "index documents " + documentIds(documents);

        gateway.awaitTaskSucceeded(gateway.addDocuments(documents, action), action);
    }

    @Override
    public void delete(String documentId) {

        String action = "delete document " + documentId;

        gateway.awaitTaskSucceeded(gateway.deleteDocument(documentId, action), action);
    }

    @Override
    public Page<SearchResultDto> search(String query, GameSearchFilter filter, Pageable pageable) {

        SearchRequest request = SearchRequest.builder()
                .q(query == null ? "" : query)
                .filter(filterBuilder.build(filter))
                .page(pageable.getPageNumber() + 1)
                .hitsPerPage(pageable.getPageSize())
                .build();

        SearchResultPaginated result = gateway.searchPaginated(request, "search '" + query + "'");

        List<SearchHitRef> hits = result.getHits().stream()
                .map(MeiliGameSearchService::toHitRefOrNull)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(hydrator.hydrate(hits), pageable, result.getTotalHits());
    }

    @Override
    public ReindexResultDto reindexAll() {

        ensureIndexSettings();

        String clearAction = "clear index " + gateway.indexUid();
        gateway.awaitTaskOrThrow(gateway.deleteAllDocuments(clearAction), clearAction);

        long games = gateway.pushAll(reindexBatchSize, documentReader::readGames);
        long expansions = gateway.pushAll(reindexBatchSize, documentReader::readExpansions);

        log.info("Reindexed {} games and {} expansions into {}", games, expansions, gateway.indexUid());

        return new ReindexResultDto(games, expansions);
    }

    public void ensureIndexSettings() {

        gateway.ensureIndexSettings(SEARCHABLE_ATTRIBUTES, FILTERABLE_ATTRIBUTES);
    }

    private static String documentIds(List<GameSearchDocument> documents) {

        return documents.stream().map(GameSearchDocument::id).collect(Collectors.joining(", "));
    }

    private static SearchHitRef toHitRefOrNull(HashMap<String, Object> hit) {

        try {
            return new SearchHitRef(
                    ContentModerationTargetType.valueOf(String.valueOf(hit.get("targetType"))),
                    ((Number) hit.get("targetId")).longValue());
        } catch (NullPointerException | ClassCastException | IllegalArgumentException e) {
            log.warn("Skipping malformed search hit {} - reindex to clean it up", hit.get("id"), e);
            return null;
        }
    }
}
