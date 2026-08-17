package pl.m22.gamehive.game.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.exceptions.MeilisearchCommunicationException;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.exceptions.MeilisearchTimeoutException;
import com.meilisearch.sdk.json.GsonJsonHandler;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kontrakt bramy jako WSPÓLNEJ infrastruktury dwóch indeksów: atrybuty są parametrem (nie stałą indeksu
 * treści), a czekanie na zadanie ma dwa warianty — cichy dla ścieżki zdarzeniowej i rzucający dla reindeksu.
 * Zachowanie w kontekście indeksu gier pokrywa {@code MeiliGameSearchServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class MeiliIndexGatewayTest {

    private static final String INDEX_UID = "gamehive_taxonomy";

    @Mock Client client;
    @Mock Index index;

    private MeiliIndexGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MeiliIndexGateway(client, new GsonJsonHandler(), INDEX_UID, Duration.ofSeconds(60));
        lenient().when(client.index(INDEX_UID)).thenReturn(index);
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
    @DisplayName("indexUid() oddaje uid, z którym brama została zbudowana — jedna instancja na indeks")
    void indexUid_isTheOneGatewayWasBuiltWith() {
        assertThat(gateway.indexUid()).isEqualTo(INDEX_UID);
    }

    @Test
    @DisplayName("addDocuments serializuje listę jako tablicę JSON z kluczem głównym 'id'")
    void addDocuments_encodesListWithPrimaryKey() {
        TaskInfo indexTask = enqueuedTask(1);
        when(index.addDocuments(anyString(), eq("id"))).thenReturn(indexTask);

        gateway.addDocuments(List.of(Map.of("id", "publisher-3")), "index documents publisher-3");

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(index).addDocuments(json.capture(), eq("id"));
        assertThat(json.getValue()).startsWith("[").contains("\"id\":\"publisher-3\"");
    }

    @Test
    @DisplayName("ensureIndexSettings nakłada PRZEKAZANE atrybuty — brama nie zna kształtu dokumentu")
    void ensureIndexSettings_appliesGivenAttributes() {
        String[] searchable = {"name"};
        String[] filterable = {"targetType", "status"};

        gateway.ensureIndexSettings(searchable, filterable);

        verify(client).createIndex(INDEX_UID, "id");
        verify(index).updateSearchableAttributesSettings(searchable);
        verify(index).updateFilterableAttributesSettings(filterable);
    }

    @Test
    @DisplayName("istniejący indeks nie jest błędem — ustawienia i tak zostają nałożone")
    void ensureIndexSettings_toleratesAlreadyExistingIndex() {
        MeilisearchApiException alreadyExists = mock(MeilisearchApiException.class);
        when(alreadyExists.getCode()).thenReturn("index_already_exists");
        when(client.createIndex(INDEX_UID, "id")).thenThrow(alreadyExists);

        assertThatNoException().isThrownBy(() ->
                gateway.ensureIndexSettings(new String[]{"name"}, new String[]{"targetType"}));

        verify(index).updateSearchableAttributesSettings(any());
        verify(index).updateFilterableAttributesSettings(any());
    }

    @Test
    @DisplayName("inny błąd przy zakładaniu indeksu przerywa konfigurację -> SEARCH_FAILED")
    void ensureIndexSettings_otherApiErrorAborts() {
        MeilisearchApiException invalidUid = mock(MeilisearchApiException.class);
        when(invalidUid.getCode()).thenReturn("invalid_index_uid");
        when(client.createIndex(INDEX_UID, "id")).thenThrow(invalidUid);

        assertThatThrownBy(() -> gateway.ensureIndexSettings(new String[]{"name"}, new String[]{"targetType"}))
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_FAILED);

        verify(index, never()).updateSearchableAttributesSettings(any());
    }

    @Test
    @DisplayName("zadanie SUCCEEDED -> awaitTaskSucceeded=true, po doczekaniu na jawnym timeoucie 60 s")
    void awaitTaskSucceeded_whenSucceeded_returnsTrue() {
        TaskInfo taskInfo = enqueuedTask(5);
        stubTaskStatus(5, TaskStatus.SUCCEEDED);

        assertThat(gateway.awaitTaskSucceeded(taskInfo, "index documents publisher-3")).isTrue();

        verify(index).waitForTask(5, 60000, 50);   // NIE bezargumentowe waitForTask, które ukrywa timeout 5 s
    }

    @Test
    @DisplayName("zadanie FAILED -> awaitTaskSucceeded=false BEZ wyjątku (ścieżka zdarzeniowa jest po committcie)")
    void awaitTaskSucceeded_whenFailed_returnsFalseWithoutThrowing() {
        TaskInfo taskInfo = enqueuedTask(6);
        stubTaskStatus(6, TaskStatus.FAILED);

        assertThat(gateway.awaitTaskSucceeded(taskInfo, "index documents publisher-3")).isFalse();
    }

    @Test
    @DisplayName("timeout doczekania -> false i BRAK pytania o status (nie ma czego pytać)")
    void awaitTaskSucceeded_whenTimeout_returnsFalseAndSkipsGetTask() {
        TaskInfo taskInfo = enqueuedTask(7);
        doThrow(new MeilisearchTimeoutException()).when(index).waitForTask(eq(7), anyInt(), anyInt());

        assertThat(gateway.awaitTaskSucceeded(taskInfo, "index documents publisher-3")).isFalse();

        verify(index, never()).getTask(anyInt());
    }

    @Test
    @DisplayName("awaitTaskOrThrow na niepotwierdzonym zadaniu -> SEARCH_FAILED (admin musi zobaczyć porażkę)")
    void awaitTaskOrThrow_whenNotConfirmed_throwsSearchFailed() {
        TaskInfo taskInfo = enqueuedTask(8);
        stubTaskStatus(8, TaskStatus.FAILED);

        assertThatThrownBy(() -> gateway.awaitTaskOrThrow(taskInfo, "clear index " + INDEX_UID))
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_FAILED);
    }

    @Test
    @DisplayName("awaitTaskOrThrow na SUCCEEDED przechodzi bez wyjątku")
    void awaitTaskOrThrow_whenSucceeded_passes() {
        TaskInfo taskInfo = enqueuedTask(9);
        stubTaskStatus(9, TaskStatus.SUCCEEDED);

        assertThatNoException().isThrownBy(() -> gateway.awaitTaskOrThrow(taskInfo, "clear index " + INDEX_UID));
    }

    @Test
    @DisplayName("host nieosiągalny -> SEARCH_INDEX_UNAVAILABLE (503)")
    void call_whenUnreachable_mapsToIndexUnavailable() {
        when(index.deleteDocument("publisher-3"))
                .thenThrow(new MeilisearchCommunicationException("connection refused"));

        assertThatThrownBy(() -> gateway.deleteDocument("publisher-3", "delete document publisher-3"))
                .isInstanceOf(InfrastructureException.class)
                .extracting(exception -> ((InfrastructureException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SEARCH_INDEX_UNAVAILABLE);
    }

    @Test
    @DisplayName("inny błąd SDK -> SEARCH_FAILED (500), a komunikat nie wynosi uid indeksu na zewnątrz")
    void call_whenRejected_mapsToSearchFailedWithoutLeakingUid() {
        when(index.deleteAllDocuments()).thenThrow(new MeilisearchException("index is locked"));

        assertThatThrownBy(() -> gateway.deleteAllDocuments("clear index " + INDEX_UID))
                .isInstanceOf(InfrastructureException.class)
                .hasMessage(ErrorCode.SEARCH_FAILED.getDefaultMessage())
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain(INDEX_UID));
    }
}
