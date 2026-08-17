package pl.m22.gamehive.game.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchCommunicationException;
import com.meilisearch.sdk.json.GsonJsonHandler;
import com.meilisearch.sdk.model.SearchResultPaginated;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.dto.AuthorDto;
import pl.m22.gamehive.game.dto.PublisherDto;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.model.TaxonomyTargetType;
import pl.m22.gamehive.game.search.config.MeiliProperties;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;
import pl.m22.gamehive.game.search.dto.TaxonomyReindexCounts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeiliTaxonomySuggestServiceTest {

    private static final String INDEX_UID = "gamehive_taxonomy";

    @Mock Client client;
    @Mock Index index;
    @Mock TaxonomySuggestionHydrator hydrator;
    @Mock TaxonomyDocumentReader documentReader;

    private MeiliTaxonomySuggestService service;

    @BeforeEach
    void setUp() {
        MeiliProperties properties = new MeiliProperties();
        properties.setTaxonomyIndexUid(INDEX_UID);
        properties.setReindexBatchSize(2);   // mały batch, żeby reindeks przeszedł przez dwie strony

        MeiliIndexGateway gateway = new MeiliIndexGateway(client, new GsonJsonHandler(), INDEX_UID,
                properties.getTaskWaitTimeout());
        service = new MeiliTaxonomySuggestService(gateway, hydrator, documentReader, properties);
        lenient().when(client.index(INDEX_UID)).thenReturn(index);
    }

    private static TaxonomyDocument publisherDocument(String id, long targetId, String name) {
        return new TaxonomyDocument(id, TaxonomyTargetType.PUBLISHER, targetId, name, TaxonomyStatus.PENDING);
    }

    private static TaxonomyDocument authorDocument(String id, long targetId, String name) {
        return new TaxonomyDocument(id, TaxonomyTargetType.AUTHOR, targetId, name, TaxonomyStatus.APPROVED);
    }

    private static HashMap<String, Object> hit(String id, Double targetId) {
        HashMap<String, Object> hit = new HashMap<>();
        hit.put("id", id);
        hit.put("targetId", targetId);      // Gson oddaje liczby jako Double
        return hit;
    }

    /** Mock trzeba zbudować PRZED wejściem w {@code when(...)} — inaczej Mockito zgłasza UnfinishedStubbing. */
    private static TaskInfo enqueuedTask(int taskUid) {
        TaskInfo taskInfo = mock(TaskInfo.class);
        lenient().when(taskInfo.getTaskUid()).thenReturn(taskUid);
        return taskInfo;
    }

    private void stubTaskStatus(int taskUid, TaskStatus status) {
        Task task = mock(Task.class);
        lenient().when(task.getStatus()).thenReturn(status);
        when(index.getTask(taskUid)).thenReturn(task);
    }

    @Test
    @DisplayName("index() wysyła dokument taksonomii jako tablicę JSON z kluczem głównym 'id'")
    void index_sendsTaxonomyDocumentJson() {
        TaskInfo indexTask = enqueuedTask(1);
        when(index.addDocuments(anyString(), eq("id"))).thenReturn(indexTask);
        stubTaskStatus(1, TaskStatus.SUCCEEDED);

        service.index(List.of(publisherDocument("publisher-3", 3L, "Pending Games")));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(index).addDocuments(json.capture(), eq("id"));
        assertThat(json.getValue())
                .startsWith("[")
                .contains("\"id\":\"publisher-3\"")
                .contains("\"targetType\":\"PUBLISHER\"")
                .contains("\"targetId\":3")
                .contains("\"name\":\"Pending Games\"")
                .contains("\"status\":\"PENDING\"");
    }

    @Test
    @DisplayName("index() z partią wydawców i autorów -> JEDNO addDocuments")
    void index_sendsWholeBatchInOneCall() {
        TaskInfo batchTask = enqueuedTask(2);
        when(index.addDocuments(anyString(), eq("id"))).thenReturn(batchTask);
        stubTaskStatus(2, TaskStatus.SUCCEEDED);

        service.index(List.of(publisherDocument("publisher-3", 3L, "Pending Games"),
                authorDocument("author-1", 1L, "Uwe Rosenberg")));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(index, times(1)).addDocuments(json.capture(), eq("id"));
        assertThat(json.getValue()).contains("\"id\":\"publisher-3\"").contains("\"id\":\"author-1\"");
    }

    @Test
    @DisplayName("pusta partia -> zero wywołań HTTP")
    void index_withEmptyBatch_doesNothing() {
        service.index(List.of());

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("delete() usuwa dokument po prefiksowanym id")
    void delete_removesDocumentById() {
        TaskInfo deleteTask = enqueuedTask(3);
        when(index.deleteDocument("author-1")).thenReturn(deleteTask);
        stubTaskStatus(3, TaskStatus.SUCCEEDED);

        service.delete("author-1");

        verify(index).deleteDocument("author-1");
        verify(index).waitForTask(3, 60000, 50);
    }

    @Test
    @DisplayName("suggestPublishers: filtr targetType, limit jako hitsPerPage, strona 1, wynik hydratowany z bazy")
    void suggestPublishers_buildsRequestAndHydrates() {
        SearchResultPaginated meiliResult = mock(SearchResultPaginated.class);
        when(meiliResult.getHits()).thenReturn(new ArrayList<>(List.of(hit("publisher-3", 3.0d))));
        when(index.search(any(SearchRequest.class))).thenReturn(meiliResult);
        PublisherDto expected = new PublisherDto(3L, "Pending Games", TaxonomyStatus.PENDING);
        when(hydrator.hydratePublishers(List.of(3L))).thenReturn(List.of(expected));

        assertThat(service.suggestPublishers("pend", 5)).containsExactly(expected);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(index).search(request.capture());
        assertThat(request.getValue().getQ()).isEqualTo("pend");
        assertThat(request.getValue().getPage()).isEqualTo(1);      // Meili liczy strony od 1
        assertThat(request.getValue().getHitsPerPage()).isEqualTo(5);
        assertThat(request.getValue().getFilter()).containsExactly("targetType = PUBLISHER");
    }

    @Test
    @DisplayName("suggestAuthors filtruje targetType = AUTHOR i hydratuje autorów")
    void suggestAuthors_filtersByAuthorType() {
        SearchResultPaginated meiliResult = mock(SearchResultPaginated.class);
        when(meiliResult.getHits()).thenReturn(new ArrayList<>(List.of(hit("author-1", 1.0d))));
        when(index.search(any(SearchRequest.class))).thenReturn(meiliResult);
        AuthorDto expected = new AuthorDto(1L, "Uwe", "Rosenberg", TaxonomyStatus.APPROVED);
        when(hydrator.hydrateAuthors(List.of(1L))).thenReturn(List.of(expected));

        assertThat(service.suggestAuthors("uwe", 10)).containsExactly(expected);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(index).search(request.capture());
        assertThat(request.getValue().getFilter()).containsExactly("targetType = AUTHOR");
    }

    @Test
    @DisplayName("brak frazy -> puste zapytanie, filtr typu zostaje (podpowiedzi obejmują wszystkie statusy)")
    void suggest_withoutQuery_sendsEmptyQueryAndKeepsTypeFilter() {
        SearchResultPaginated meiliResult = mock(SearchResultPaginated.class);
        when(meiliResult.getHits()).thenReturn(new ArrayList<>());
        when(index.search(any(SearchRequest.class))).thenReturn(meiliResult);
        when(hydrator.hydratePublishers(List.of())).thenReturn(List.of());

        service.suggestPublishers(null, 10);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(index).search(request.capture());
        assertThat(request.getValue().getQ()).isEmpty();
        assertThat(request.getValue().getFilter()).containsExactly("targetType = PUBLISHER");
    }

    @Test
    @DisplayName("uszkodzone trafienie jest pomijane, a nie wywraca podpowiedzi na 500")
    void suggest_skipsMalformedHit() {
        HashMap<String, Object> noTargetId = new HashMap<>();
        noTargetId.put("id", "publisher-666");
        SearchResultPaginated meiliResult = mock(SearchResultPaginated.class);
        when(meiliResult.getHits()).thenReturn(new ArrayList<>(List.of(noTargetId, hit("publisher-3", 3.0d))));
        when(index.search(any(SearchRequest.class))).thenReturn(meiliResult);
        when(hydrator.hydratePublishers(List.of(3L))).thenReturn(List.of());

        assertThatNoException().isThrownBy(() -> service.suggestPublishers("x", 10));

        verify(hydrator).hydratePublishers(List.of(3L));
    }

    @Test
    @DisplayName("ensureIndexSettings: szukane po 'name', filtrowalne po targetType i status")
    void ensureIndexSettings_appliesTaxonomyAttributes() {
        service.ensureIndexSettings();

        verify(client).createIndex(INDEX_UID, "id");
        verify(index).updateSearchableAttributesSettings(new String[]{"name"});
        verify(index).updateFilterableAttributesSettings(new String[]{"targetType", "status"});
    }

    @Test
    @DisplayName("reindexAll: konfiguruje indeks, czyści go i wypycha wydawców oraz autorów partiami")
    void reindexAll_clearsAndPushesBothTypes() {
        // batch = 2: wydawcy na dwóch stronach (3 wpisy), autorzy na jednej (2 wpisy)
        when(documentReader.readPublishers(any())).thenReturn(
                new PageImpl<>(List.of(publisherDocument("publisher-1", 1L, "Rio Grande Games"),
                        publisherDocument("publisher-2", 2L, "Z-Man Games")), PageRequest.of(0, 2), 3),
                new PageImpl<>(List.of(publisherDocument("publisher-3", 3L, "Pending Games")),
                        PageRequest.of(1, 2), 3));
        when(documentReader.readAuthors(any())).thenReturn(
                new PageImpl<>(List.of(authorDocument("author-1", 1L, "Uwe Rosenberg"),
                        authorDocument("author-2", 2L, "Reiner Knizia")), PageRequest.of(0, 2), 2));
        TaskInfo clearTask = enqueuedTask(1);
        TaskInfo batch1 = enqueuedTask(2);
        TaskInfo batch2 = enqueuedTask(3);
        TaskInfo batch3 = enqueuedTask(4);
        when(index.deleteAllDocuments()).thenReturn(clearTask);
        when(index.addDocuments(anyString(), eq("id"))).thenReturn(batch1, batch2, batch3);
        for (int taskUid = 1; taskUid <= 4; taskUid++) {
            stubTaskStatus(taskUid, TaskStatus.SUCCEEDED);
        }

        assertThat(service.reindexAll()).isEqualTo(new TaxonomyReindexCounts(3, 2));

        InOrder order = inOrder(client, index);
        order.verify(client).createIndex(INDEX_UID, "id");
        order.verify(index).updateSearchableAttributesSettings(new String[]{"name"});
        order.verify(index).updateFilterableAttributesSettings(new String[]{"targetType", "status"});
        order.verify(index).deleteAllDocuments();
        order.verify(index).waitForTask(1, 60000, 50);
        order.verify(index, times(3)).addDocuments(anyString(), eq("id"));
        verify(index).waitForTask(2, 60000, 50);
        verify(index).waitForTask(3, 60000, 50);
        verify(index).waitForTask(4, 60000, 50);
    }

    @Test
    @DisplayName("nieudane czyszczenie indeksu -> SEARCH_FAILED, bez czytania bazy i bez fałszywych liczników")
    void reindexAll_failedClearTask_doesNotReportSuccess() {
        TaskInfo clearTask = enqueuedTask(1);
        when(index.deleteAllDocuments()).thenReturn(clearTask);
        stubTaskStatus(1, TaskStatus.FAILED);

        assertThatThrownBy(() -> service.reindexAll())
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_FAILED);

        verifyNoInteractions(documentReader);
        verify(index, never()).addDocuments(anyString(), eq("id"));
    }

    @Test
    @DisplayName("porażka partii wydawców przerywa reindeks — autorzy nie są już czytani")
    void reindexAll_failedPublisherBatch_abortsBeforeAuthors() {
        when(documentReader.readPublishers(any())).thenReturn(
                new PageImpl<>(List.of(publisherDocument("publisher-1", 1L, "Rio Grande Games")),
                        PageRequest.of(0, 2), 1));
        TaskInfo clearTask = enqueuedTask(1);
        TaskInfo batchTask = enqueuedTask(2);
        when(index.deleteAllDocuments()).thenReturn(clearTask);
        when(index.addDocuments(anyString(), eq("id"))).thenReturn(batchTask);
        stubTaskStatus(1, TaskStatus.SUCCEEDED);
        stubTaskStatus(2, TaskStatus.FAILED);

        assertThatThrownBy(() -> service.reindexAll()).isInstanceOf(InfrastructureException.class);

        verify(documentReader, never()).readAuthors(any());
    }

    @Test
    @DisplayName("pusta taksonomia -> zerowe liczniki, indeks tylko wyczyszczony")
    void reindexAll_withNoTaxonomy_returnsZeros() {
        when(documentReader.readPublishers(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 2), 0));
        when(documentReader.readAuthors(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 2), 0));
        TaskInfo clearTask = enqueuedTask(1);
        when(index.deleteAllDocuments()).thenReturn(clearTask);
        stubTaskStatus(1, TaskStatus.SUCCEEDED);

        assertThat(service.reindexAll()).isEqualTo(new TaxonomyReindexCounts(0, 0));

        verify(index, never()).addDocuments(anyString(), eq("id"));
    }

    @Test
    @DisplayName("Meili nieosiągalne przy podpowiedzi -> SEARCH_INDEX_UNAVAILABLE, a fraza nie wycieka do komunikatu")
    void suggest_whenUnreachable_mapsToIndexUnavailableWithoutLeakingQuery() {
        when(index.search(any(SearchRequest.class)))
                .thenThrow(new MeilisearchCommunicationException("connection refused"));

        assertThatThrownBy(() -> service.suggestPublishers("sekretna fraza uzytkownika", 10))
                .isInstanceOf(InfrastructureException.class)
                .hasMessage(ErrorCode.SEARCH_INDEX_UNAVAILABLE.getDefaultMessage())
                .satisfies(exception -> assertThat(exception.getMessage())
                        .doesNotContain("sekretna fraza uzytkownika")
                        .doesNotContain(INDEX_UID));
    }

    @Test
    @DisplayName("awaria indeksowania mapuje się na InfrastructureException (listener ją potem łyka)")
    void indexFailure_mapsToInfrastructureException() {
        doThrow(new MeilisearchCommunicationException("connection refused"))
                .when(index).addDocuments(anyString(), eq("id"));

        assertThatThrownBy(() -> service.index(List.of(publisherDocument("publisher-3", 3L, "Pending Games"))))
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_INDEX_UNAVAILABLE);
    }
}
