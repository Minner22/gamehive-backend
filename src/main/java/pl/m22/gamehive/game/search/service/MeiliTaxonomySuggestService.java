package pl.m22.gamehive.game.search.service;

import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.SearchResultPaginated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.game.dto.AuthorDto;
import pl.m22.gamehive.game.dto.PublisherDto;
import pl.m22.gamehive.game.model.TaxonomyTargetType;
import pl.m22.gamehive.game.search.config.MeiliClientConfig;
import pl.m22.gamehive.game.search.config.MeiliProperties;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;
import pl.m22.gamehive.game.search.dto.TaxonomyReindexCounts;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "gamehive.search.enabled", matchIfMissing = true)
public class MeiliTaxonomySuggestService implements TaxonomySuggestService {

    static final String[] SEARCHABLE_ATTRIBUTES = {"name"};

    static final String[] FILTERABLE_ATTRIBUTES = {"targetType", "status"};

    private final MeiliIndexGateway gateway;
    private final TaxonomySuggestionHydrator hydrator;
    private final TaxonomyDocumentReader documentReader;
    private final int reindexBatchSize;

    public MeiliTaxonomySuggestService(@Qualifier(MeiliClientConfig.TAXONOMY_GATEWAY) MeiliIndexGateway gateway,
                                       TaxonomySuggestionHydrator hydrator,
                                       TaxonomyDocumentReader documentReader,
                                       MeiliProperties properties) {

        this.gateway = gateway;
        this.hydrator = hydrator;
        this.documentReader = documentReader;
        this.reindexBatchSize = properties.getReindexBatchSize();
    }

    @Override
    public List<PublisherDto> suggestPublishers(String query, int limit) {

        return hydrator.hydratePublishers(searchTargetIds(query, TaxonomyTargetType.PUBLISHER, limit));
    }

    @Override
    public List<AuthorDto> suggestAuthors(String query, int limit) {

        return hydrator.hydrateAuthors(searchTargetIds(query, TaxonomyTargetType.AUTHOR, limit));
    }

    @Override
    public void index(List<TaxonomyDocument> documents) {

        if (documents.isEmpty()) {
            return;
        }

        String action = "index taxonomy documents " + documentIds(documents);

        gateway.awaitTaskSucceeded(gateway.addDocuments(documents, action), action);
    }

    @Override
    public void delete(String documentId) {

        String action = "delete taxonomy document " + documentId;

        gateway.awaitTaskSucceeded(gateway.deleteDocument(documentId, action), action);
    }

    @Override
    public TaxonomyReindexCounts reindexAll() {

        ensureIndexSettings();

        String clearAction = "clear index " + gateway.indexUid();
        gateway.awaitTaskOrThrow(gateway.deleteAllDocuments(clearAction), clearAction);

        long publishers = gateway.pushAll(reindexBatchSize, documentReader::readPublishers);
        long authors = gateway.pushAll(reindexBatchSize, documentReader::readAuthors);

        log.info("Reindexed {} publishers and {} authors into {}", publishers, authors, gateway.indexUid());

        return new TaxonomyReindexCounts(publishers, authors);
    }

    public void ensureIndexSettings() {

        gateway.ensureIndexSettings(SEARCHABLE_ATTRIBUTES, FILTERABLE_ATTRIBUTES);
    }

    private List<Long> searchTargetIds(String query, TaxonomyTargetType targetType, int limit) {

        SearchRequest request = SearchRequest.builder()
                .q(query == null ? "" : query)
                .filter(new String[]{"targetType = " + targetType.name()})
                .page(1)
                .hitsPerPage(limit)
                .build();

        SearchResultPaginated result = gateway.searchPaginated(request,
                "suggest " + targetType + " '" + query + "'");

        return result.getHits().stream()
                .map(MeiliTaxonomySuggestService::toTargetIdOrNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String documentIds(List<TaxonomyDocument> documents) {

        return documents.stream().map(TaxonomyDocument::id).collect(Collectors.joining(", "));
    }

    private static Long toTargetIdOrNull(HashMap<String, Object> hit) {

        try {
            return ((Number) hit.get("targetId")).longValue();
        } catch (NullPointerException | ClassCastException e) {
            log.warn("Skipping malformed taxonomy hit {} - reindex to clean it up", hit.get("id"), e);
            return null;
        }
    }
}
