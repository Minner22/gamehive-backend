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
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.config.MeiliProperties;
import pl.m22.gamehive.game.search.dto.*;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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
    private final ApprovedContentDocumentReader documentReader;
    private final String indexUid;
    private final int reindexBatchSize;

    public MeiliGameSearchService(Client client,
                                  JsonHandler jsonHandler,
                                  MeiliFilterBuilder filterBuilder,
                                  SearchResultHydrator hydrator,
                                  ApprovedContentDocumentReader documentReader,
                                  MeiliProperties properties) {

        this.client = client;
        this.jsonHandler = jsonHandler;
        this.filterBuilder = filterBuilder;
        this.hydrator = hydrator;
        this.documentReader = documentReader;
        this.indexUid = properties.getIndexUid();
        this.reindexBatchSize = properties.getReindexBatchSize();
    }

    @Override
    public void index(GameSearchDocument document) {

        TaskInfo task = call(() -> index().addDocuments(jsonHandler.encode(List.of(document)), PRIMARY_KEY),
                "index document " + document.id());

        log.debug("Enqueued Meili task {} to index document {}", task.getTaskUid(), document.id());
    }

    @Override
    public void delete(String documentId) {

        TaskInfo task = call(() -> index().deleteDocument(documentId), "delete document " + documentId);

        log.debug("Enqueued Meili task {} to delete document {}", task.getTaskUid(), documentId);
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
                .map(MeiliGameSearchService::toHitRefOrNull)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(hydrator.hydrate(hits), pageable, result.getTotalHits());
    }

    @Override
    public ReindexResultDto reindexAll() {

        ensureIndexSettings();
        awaitTask(call(() -> index().deleteAllDocuments(), "clear index " + indexUid), "clear index");

        long games = pushAll(documentReader::readGames);
        long expansions = pushAll(documentReader::readExpansions);

        log.info("Reindexed {} games and {} expansions into {}", games, expansions, indexUid);

        return new ReindexResultDto(games, expansions);
    }

    private long pushAll(Function<Pageable, Page<GameSearchDocument>> reader) {

        long pushed = 0;
        Pageable pageable = PageRequest.of(0, reindexBatchSize, Sort.by("id"));

        while (true) {
            Page<GameSearchDocument> batch = reader.apply(pageable);

            if (batch.hasContent()) {
                String action = "index batch of " + batch.getNumberOfElements() + " documents";
                TaskInfo task = call(
                        () -> index().addDocuments(jsonHandler.encode(batch.getContent()), PRIMARY_KEY), action);
                awaitTask(task, action);
                pushed += batch.getNumberOfElements();
            }
            if (!batch.hasNext()) {
                return pushed;
            }
            pageable = pageable.next();
        }
    }

    private void awaitTask(TaskInfo taskInfo, String action) {

        int taskUid = taskInfo.getTaskUid();
        Task task = call(() -> {
            index().waitForTask(taskUid);
            return index().getTask(taskUid);
        }, action);

        if (task.getStatus() != TaskStatus.SUCCEEDED) {
            log.error("Meili task {} ({}) finished with status {}: {}", taskUid, action, task.getStatus(),
                    task.getError() != null ? task.getError().getMessage() : "no error details");
            throw new InfrastructureException(ErrorCode.SEARCH_FAILED);
        }
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

    private <T> T call(MeiliCall<T> meiliCall, String action) {

        try {
            return meiliCall.execute();
        } catch (MeilisearchCommunicationException | MeilisearchTimeoutException e) {
            log.error("Meilisearch unreachable while trying to {}", action, e);
            throw new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE);
        } catch (MeilisearchException e) {
            log.error("Meilisearch rejected request while trying to {}", action, e);
            throw new InfrastructureException(ErrorCode.SEARCH_FAILED);
        }
    }

    @FunctionalInterface
    private interface MeiliCall<T> {

        T execute() throws MeilisearchException;
    }
}
