package pl.m22.gamehive.game.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.exceptions.MeilisearchCommunicationException;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.exceptions.MeilisearchTimeoutException;
import com.meilisearch.sdk.json.JsonHandler;
import com.meilisearch.sdk.model.SearchResultPaginated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.config.MeiliProperties;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;
import pl.m22.gamehive.game.search.dto.GameSearchFilter;
import pl.m22.gamehive.game.search.dto.SearchHitRef;
import pl.m22.gamehive.game.search.dto.SearchResultDto;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(name = "gamehive.search.enabled", matchIfMissing = true)
public class MeiliGameSearchService implements GameSearchService {

    static final String[] SEARCHABLE_ATTRIBUTES = {"title", "description", "baseGameTitle"};

    static final String[] FILTERABLE_ATTRIBUTES = {"targetType", "publisherIds", "categoryIds", "mechanicIds",
            "authorIds", "baseGameId", "minPlayers", "maxPlayers", "playingTimeMinutes", "yearPublished", "minAge"};

    private static final String PRIMARY_KEY = "id";
    private static final String INDEX_ALREADY_EXISTS = "index_already_exists";

    private final Client client;
    private final JsonHandler jsonHandler;
    private final MeiliFilterBuilder filterBuilder;
    private final SearchResultHydrator hydrator;
    private final String indexUid;

    public MeiliGameSearchService(Client client,
                                  JsonHandler jsonHandler,
                                  MeiliFilterBuilder filterBuilder,
                                  SearchResultHydrator hydrator,
                                  MeiliProperties properties) {

        this.client = client;
        this.jsonHandler = jsonHandler;
        this.filterBuilder = filterBuilder;
        this.hydrator = hydrator;
        this.indexUid = properties.getIndexUid();
    }

    @Override
    public void index(GameSearchDocument document) {

        call(() -> index().addDocuments(jsonHandler.encode(List.of(document)), PRIMARY_KEY),
                "index document " + document.id());
    }

    @Override
    public void delete(String documentId) {

        call(() -> index().deleteDocument(documentId), "delete document " + documentId);
    }

    @Override
    public Page<SearchResultDto> search(String query, GameSearchFilter filter, Pageable pageable) {

        SearchRequest request = SearchRequest.builder()
                .q(query == null ? "" : query)
                .filter(filterBuilder.build(filter))
                .page(pageable.getPageNumber() + 1)
                .hitsPerPage(pageable.getPageSize())
                .build();

        SearchResultPaginated result = call(() -> (SearchResultPaginated) index().search(request),
                "search '" + query + "'");

        List<SearchHitRef> hits = result.getHits().stream()
                .map(MeiliGameSearchService::toHitRef)
                .toList();

        return new PageImpl<>(hydrator.hydrate(hits), pageable, result.getTotalHits());
    }

    public void ensureIndexSettings() {

        call(() -> {
            createIndexIfMissing();
            index().updateSearchableAttributesSettings(SEARCHABLE_ATTRIBUTES);
            return index().updateFilterableAttributesSettings(FILTERABLE_ATTRIBUTES);
        }, "configure index " + indexUid);
    }

    private void createIndexIfMissing() {

        try {
            client.createIndex(indexUid, PRIMARY_KEY);
        } catch (MeilisearchApiException e) {
            if (!INDEX_ALREADY_EXISTS.equals(e.getCode())) {
                throw e;
            }
            log.debug("Meili index {} already exists", indexUid);
        }
    }

    private Index index() {

        return client.index(indexUid);
    }

    private static SearchHitRef toHitRef(HashMap<String, Object> hit) {

        return new SearchHitRef(
                ContentModerationTargetType.valueOf(String.valueOf(hit.get("targetType"))),
                ((Number) hit.get("targetId")).longValue());
    }

    private <T> T call(MeiliCall<T> meiliCall, String action) {

        try {
            return meiliCall.execute();
        } catch (MeilisearchCommunicationException | MeilisearchTimeoutException e) {
            log.error("Meilisearch unreachable while trying to {}", action, e);
            throw new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE,
                    "Cannot " + action + " - search engine unavailable");
        } catch (MeilisearchException e) {
            log.error("Meilisearch rejected request while trying to {}", action, e);
            throw new InfrastructureException(ErrorCode.SEARCH_FAILED, "Cannot " + action + " - search engine error");
        }
    }

    @FunctionalInterface
    private interface MeiliCall<T> {

        T execute() throws MeilisearchException;
    }
}
