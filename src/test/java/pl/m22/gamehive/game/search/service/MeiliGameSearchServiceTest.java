package pl.m22.gamehive.game.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.exceptions.MeilisearchCommunicationException;
import com.meilisearch.sdk.exceptions.MeilisearchException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.config.MeiliProperties;
import pl.m22.gamehive.game.search.dto.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeiliGameSearchServiceTest {

    private static final String INDEX_UID = "gamehive_content";

    @Mock Client client;
    @Mock Index index;
    @Mock SearchResultHydrator hydrator;
    @Mock ApprovedContentDocumentReader documentReader;

    private MeiliGameSearchService service;

    @BeforeEach
    void setUp() {
        MeiliProperties properties = new MeiliProperties();
        properties.setHost("http://localhost:7700");
        properties.setApiKey("test-key");
        properties.setIndexUid(INDEX_UID);
        properties.setReindexBatchSize(2);   // mały batch, żeby test reindeksu przeszedł przez dwie strony

        service = new MeiliGameSearchService(client, new GsonJsonHandler(), new MeiliFilterBuilder(),
                hydrator, documentReader, properties);
        lenient().when(client.index(INDEX_UID)).thenReturn(index);
    }

    private static GameSearchDocument gameDocument() {
        return gameDocument("game-1", 1L);
    }

    private static GameSearchDocument gameDocument(String id, long targetId) {
        return new GameSearchDocument(id, ContentModerationTargetType.GAME, targetId,
                "Agricola", "Klasyczna gra o rozwoju farmy.", null,
                List.of(1L, 2L), List.of(1L), List.of(1L), List.of(1L),
                1, 4, 120, 2007, 12, null);
    }

    private static GameSearchDocument expansionDocument() {
        return new GameSearchDocument("expansion-1", ContentModerationTargetType.EXPANSION, 1L,
                "Carcassonne: Rzeka", "Zatwierdzony dodatek.", "Carcassonne",
                List.of(), List.of(5L), List.of(3L), List.of(),
                2, 6, 45, null, 8, 7L);
    }

    private static GameSearchFilter emptyFilter() {
        return new GameSearchFilter(null, null, null, null, null, null, null, null, null, null);
    }

    private static HashMap<String, Object> hit(String targetType, int targetId) {
        HashMap<String, Object> hit = new HashMap<>();
        hit.put("targetType", targetType);
        hit.put("targetId", targetId);
        return hit;
    }

    private static SearchResultDto anyGameResult() {
        return SearchResultDto.of(new GameDto(1L, "Agricola", "Opis.", 1, 4, 120, 2007, 12, null,
                null, null, List.of(), List.of(), List.of(), List.of()));
    }

    /**
     * Operacje na dokumentach zwracają zakolejkowane zadanie — bez tego stuba kod nie ma czego zalogować.
     * Mock trzeba zbudować PRZED wejściem w {@code when(...)}, bo Mockito nie pozwala zagnieżdżać stubowania.
     */
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
    @DisplayName("index() wysyła dokument jako tablicę JSON z kluczem głównym 'id', bez pól null")
    void index_sendsDocumentJsonWithPrimaryKey() {
        TaskInfo indexTask = enqueuedTask(7);
        when(index.addDocuments(anyString(), eq("id"))).thenReturn(indexTask);

        service.index(gameDocument());

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(index).addDocuments(json.capture(), eq("id"));
        assertThat(json.getValue())
                .startsWith("[")
                .contains("\"id\":\"game-1\"")
                .contains("\"targetType\":\"GAME\"")
                .contains("\"title\":\"Agricola\"")
                .contains("\"publisherIds\":[1,2]")
                .doesNotContain("baseGameTitle")   // null pominięty — dodatkowe pola nie zaśmiecają indeksu
                .doesNotContain("baseGameId");
    }

    @Test
    @DisplayName("delete() usuwa dokument po prefiksowanym id")
    void delete_removesDocumentById() {
        TaskInfo deleteTask = enqueuedTask(8);
        when(index.deleteDocument("expansion-1")).thenReturn(deleteTask);

        service.delete("expansion-1");

        verify(index).deleteDocument("expansion-1");
    }

    @Test
    @DisplayName("search() przekazuje frazę, filtry i stronę do Meili, a trafienia hydratuje z bazy")
    void search_delegatesToMeiliAndHydrates() {
        SearchResultPaginated meiliResult = mock(SearchResultPaginated.class);
        when(meiliResult.getHits()).thenReturn(new ArrayList<>(List.of(hit("GAME", 1), hit("EXPANSION", 1))));
        // totalHits > offset + size, bo PageImpl „koryguje" total na ostatniej stronie i asercja badałaby
        // wtedy heurystykę Springa, a nie to, że total bierzemy z Meili
        when(meiliResult.getTotalHits()).thenReturn(42);
        when(index.search(any(SearchRequest.class))).thenReturn(meiliResult);
        when(hydrator.hydrate(anyList())).thenReturn(List.of(anyGameResult()));

        GameSearchFilter filter = new GameSearchFilter(null, null, null, null, null, null, 3, null, null, null);
        Page<SearchResultDto> page = service.search("carcassonne", filter, PageRequest.of(1, 20));

        assertThat(page.getTotalElements()).isEqualTo(42);
        assertThat(page.getNumber()).isEqualTo(1);
        // hydrator jest tu mockiem — realne odsiewanie nieaktualnych trafień pokrywa SearchResultHydratorTest
        assertThat(page.getContent()).hasSize(1);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(index).search(request.capture());
        assertThat(request.getValue().getQ()).isEqualTo("carcassonne");
        assertThat(request.getValue().getPage()).isEqualTo(2);          // Meili liczy strony od 1
        assertThat(request.getValue().getHitsPerPage()).isEqualTo(20);
        assertThat(request.getValue().getFilter()).containsExactly("minPlayers <= 3", "maxPlayers >= 3");

        verify(hydrator).hydrate(List.of(
                new SearchHitRef(ContentModerationTargetType.GAME, 1L),
                new SearchHitRef(ContentModerationTargetType.EXPANSION, 1L)));
    }

    @Test
    @DisplayName("search() bez frazy -> puste zapytanie (przeglądanie po samych filtrach)")
    void search_withoutQuery_sendsEmptyQuery() {
        SearchResultPaginated meiliResult = mock(SearchResultPaginated.class);
        when(meiliResult.getHits()).thenReturn(new ArrayList<>());
        when(meiliResult.getTotalHits()).thenReturn(0);
        when(index.search(any(SearchRequest.class))).thenReturn(meiliResult);
        when(hydrator.hydrate(anyList())).thenReturn(List.of());

        service.search(null, emptyFilter(), PageRequest.of(0, 20));

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(index).search(request.capture());
        assertThat(request.getValue().getQ()).isEmpty();
        assertThat(request.getValue().getFilter()).isNull();
    }

    @Test
    @DisplayName("ensureIndexSettings() zakłada indeks i ustawia atrybuty szukane oraz filtrowalne")
    void ensureIndexSettings_createsIndexAndAppliesSettings() {
        service.ensureIndexSettings();

        verify(client).createIndex(INDEX_UID, "id");
        verify(index).updateSearchableAttributesSettings(
                new String[]{"title", "description", "baseGameTitle"});
        verify(index).updateFilterableAttributesSettings(MeiliGameSearchService.FILTERABLE_ATTRIBUTES);
    }

    @Test
    @DisplayName("istniejący indeks nie jest błędem — ustawienia i tak zostają nałożone")
    void ensureIndexSettings_toleratesAlreadyExistingIndex() {
        MeilisearchApiException alreadyExists = mock(MeilisearchApiException.class);
        when(alreadyExists.getCode()).thenReturn("index_already_exists");
        when(client.createIndex(INDEX_UID, "id")).thenThrow(alreadyExists);

        service.ensureIndexSettings();

        verify(index).updateSearchableAttributesSettings(any());
        verify(index).updateFilterableAttributesSettings(any());
    }

    @Test
    @DisplayName("inny błąd przy zakładaniu indeksu przerywa konfigurację -> SEARCH_FAILED")
    void ensureIndexSettings_otherApiErrorAborts() {
        MeilisearchApiException invalidUid = mock(MeilisearchApiException.class);
        when(invalidUid.getCode()).thenReturn("invalid_index_uid");
        when(client.createIndex(INDEX_UID, "id")).thenThrow(invalidUid);

        assertThatThrownBy(() -> service.ensureIndexSettings())
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_FAILED);

        verify(index, never()).updateSearchableAttributesSettings(any());
    }

    @Test
    @DisplayName("reindexAll(): konfiguruje indeks, czyści go, wypycha wszystko partiami i czeka na każde zadanie")
    void reindexAll_appliesSettingsClearsAndPushesAllApproved() {
        // batch = 2: pierwsza strona pełna i z następnikiem, druga domyka zbiór (3 gry, 1 dodatek)
        when(documentReader.readGames(any())).thenReturn(
                new PageImpl<>(List.of(gameDocument("game-1", 1L), gameDocument("game-2", 2L)),
                        PageRequest.of(0, 2), 3),
                new PageImpl<>(List.of(gameDocument("game-3", 3L)), PageRequest.of(1, 2), 3));
        when(documentReader.readExpansions(any())).thenReturn(
                new PageImpl<>(List.of(expansionDocument()), PageRequest.of(0, 2), 1));
        TaskInfo clearTask = enqueuedTask(1);
        TaskInfo batch1 = enqueuedTask(2);
        TaskInfo batch2 = enqueuedTask(3);
        TaskInfo batch3 = enqueuedTask(4);
        when(index.deleteAllDocuments()).thenReturn(clearTask);
        when(index.addDocuments(anyString(), eq("id"))).thenReturn(batch1, batch2, batch3);
        for (int taskUid = 1; taskUid <= 4; taskUid++) {
            stubTaskStatus(taskUid, TaskStatus.SUCCEEDED);
        }

        ReindexResultDto result = service.reindexAll();

        assertThat(result).isEqualTo(new ReindexResultDto(3, 1));

        InOrder order = inOrder(client, index);
        order.verify(client).createIndex(INDEX_UID, "id");
        order.verify(index).updateSearchableAttributesSettings(MeiliGameSearchService.SEARCHABLE_ATTRIBUTES);
        order.verify(index).updateFilterableAttributesSettings(MeiliGameSearchService.FILTERABLE_ATTRIBUTES);
        order.verify(index).deleteAllDocuments();
        order.verify(index).waitForTask(1);
        // dwie partie gier + jedna dodatków, każda doczekana
        order.verify(index, times(3)).addDocuments(anyString(), eq("id"));
        verify(index).waitForTask(2);
        verify(index).waitForTask(3);
        verify(index).waitForTask(4);
    }

    @Test
    @DisplayName("zadanie Meili ze statusem FAILED -> SEARCH_FAILED, zamiast 200 z fałszywymi licznikami")
    void reindexAll_failedTask_doesNotReportSuccess() {
        TaskInfo clearTask = enqueuedTask(1);
        when(index.deleteAllDocuments()).thenReturn(clearTask);
        stubTaskStatus(1, TaskStatus.FAILED);

        assertThatThrownBy(() -> service.reindexAll())
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_FAILED);

        // nie ruszamy bazy ani nie dodajemy dokumentów po nieudanym czyszczeniu
        verifyNoInteractions(documentReader);
        verify(index, never()).addDocuments(anyString(), eq("id"));
    }

    @Test
    @DisplayName("porażka zadania w środku wypychania też przerywa reindeks (indeks jest już wyczyszczony)")
    void reindexAll_failedBatchTask_abortsWithoutFalseCounters() {
        when(documentReader.readGames(any())).thenReturn(
                new PageImpl<>(List.of(gameDocument("game-1", 1L)), PageRequest.of(0, 2), 1));
        TaskInfo clearTask = enqueuedTask(1);
        TaskInfo batchTask = enqueuedTask(2);
        when(index.deleteAllDocuments()).thenReturn(clearTask);
        when(index.addDocuments(anyString(), eq("id"))).thenReturn(batchTask);
        stubTaskStatus(1, TaskStatus.SUCCEEDED);
        stubTaskStatus(2, TaskStatus.FAILED);

        assertThatThrownBy(() -> service.reindexAll())
                .isInstanceOf(InfrastructureException.class);

        verify(documentReader, never()).readExpansions(any());
    }

    @Test
    @DisplayName("reindexAll() na pustej bazie -> zerowe liczniki, indeks tylko wyczyszczony")
    void reindexAll_withNoApprovedContent_returnsZeros() {
        when(documentReader.readGames(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 2), 0));
        when(documentReader.readExpansions(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 2), 0));
        TaskInfo clearTask = enqueuedTask(1);
        when(index.deleteAllDocuments()).thenReturn(clearTask);
        stubTaskStatus(1, TaskStatus.SUCCEEDED);

        assertThat(service.reindexAll()).isEqualTo(new ReindexResultDto(0, 0));

        verify(index).deleteAllDocuments();
        verify(index, never()).addDocuments(anyString(), eq("id"));
    }

    @Test
    @DisplayName("targetId z Meili przychodzi jako Double (Gson) — rzutowanie przez Number musi to znosić")
    void search_handlesDoubleTargetIdFromGson() {
        HashMap<String, Object> gsonHit = new HashMap<>();
        gsonHit.put("targetType", "GAME");
        gsonHit.put("targetId", 7.0d);          // dokładnie to, co zwraca realny silnik
        SearchResultPaginated meiliResult = mock(SearchResultPaginated.class);
        when(meiliResult.getHits()).thenReturn(new ArrayList<>(List.of(gsonHit)));
        when(meiliResult.getTotalHits()).thenReturn(1);
        when(index.search(any(SearchRequest.class))).thenReturn(meiliResult);
        when(hydrator.hydrate(anyList())).thenReturn(List.of(anyGameResult()));

        service.search("x", emptyFilter(), PageRequest.of(0, 20));

        verify(hydrator).hydrate(List.of(new SearchHitRef(ContentModerationTargetType.GAME, 7L)));
    }

    @Test
    @DisplayName("uszkodzony dokument jest pomijany, a nie wywraca całego wyszukiwania na 500")
    void search_skipsMalformedHits() {
        HashMap<String, Object> noTargetId = new HashMap<>();
        noTargetId.put("id", "game-666");
        noTargetId.put("targetType", "GAME");
        HashMap<String, Object> unknownType = new HashMap<>();
        unknownType.put("id", "thing-1");
        unknownType.put("targetType", "THING");
        unknownType.put("targetId", 1.0d);
        HashMap<String, Object> healthy = new HashMap<>();
        healthy.put("targetType", "EXPANSION");
        healthy.put("targetId", 3.0d);

        SearchResultPaginated meiliResult = mock(SearchResultPaginated.class);
        when(meiliResult.getHits()).thenReturn(new ArrayList<>(List.of(noTargetId, unknownType, healthy)));
        when(meiliResult.getTotalHits()).thenReturn(3);
        when(index.search(any(SearchRequest.class))).thenReturn(meiliResult);
        when(hydrator.hydrate(anyList())).thenReturn(List.of());

        service.search("x", emptyFilter(), PageRequest.of(0, 20));

        verify(hydrator).hydrate(List.of(new SearchHitRef(ContentModerationTargetType.EXPANSION, 3L)));
    }

    @Test
    @DisplayName("komunikat błędu nie odbija frazy zapytania ani szczegółów wewnętrznych")
    void searchFailure_doesNotLeakQueryIntoMessage() {
        when(index.search(any(SearchRequest.class)))
                .thenThrow(new MeilisearchCommunicationException("connection refused"));

        assertThatThrownBy(() -> service.search("sekretna fraza uzytkownika", emptyFilter(), PageRequest.of(0, 20)))
                .isInstanceOf(InfrastructureException.class)
                .hasMessage(ErrorCode.SEARCH_INDEX_UNAVAILABLE.getDefaultMessage())
                .satisfies(exception -> assertThat(exception.getMessage())
                        .doesNotContain("sekretna fraza uzytkownika")
                        .doesNotContain(INDEX_UID));
    }

    @Test
    @DisplayName("Meili nieosiągalne -> InfrastructureException SEARCH_INDEX_UNAVAILABLE (503)")
    void communicationFailure_mapsToIndexUnavailable() {
        when(index.search(any(SearchRequest.class)))
                .thenThrow(new MeilisearchCommunicationException("connection refused"));

        assertThatThrownBy(() -> service.search("x", emptyFilter(), PageRequest.of(0, 20)))
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_INDEX_UNAVAILABLE);
    }

    @Test
    @DisplayName("inny błąd Meili (np. odrzucone wyrażenie filtra) -> InfrastructureException SEARCH_FAILED (500)")
    void otherMeiliFailure_mapsToSearchFailed() {
        when(index.search(any(SearchRequest.class))).thenThrow(new MeilisearchException("invalid filter"));

        assertThatThrownBy(() -> service.search("x", emptyFilter(), PageRequest.of(0, 20)))
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_FAILED);
    }

    @Test
    @DisplayName("awaria indeksowania też mapuje się na InfrastructureException (listener ją potem łyka)")
    void indexFailure_mapsToInfrastructureException() {
        doThrow(new MeilisearchCommunicationException("connection refused"))
                .when(index).addDocuments(anyString(), eq("id"));

        assertThatThrownBy(() -> service.index(gameDocument()))
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_INDEX_UNAVAILABLE);
    }
}
