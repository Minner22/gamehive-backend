package pl.m22.gamehive.game.search.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.model.TaxonomyTargetType;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;
import pl.m22.gamehive.game.search.service.TaxonomySuggestService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxonomyIndexListenerTest {

    @Mock TaxonomySuggestService taxonomySuggestService;
    @InjectMocks TaxonomyIndexListener listener;

    private static TaxonomyDocument document(String id, TaxonomyTargetType targetType) {
        return new TaxonomyDocument(id, targetType, 1L, "Rio Grande Games", TaxonomyStatus.APPROVED);
    }

    @Test
    @DisplayName("UPSERT -> JEDNO index() z całą partią (jedno wywołanie HTTP na mutację)")
    void upsert_delegatesToIndexAsSingleBatch() {
        List<TaxonomyDocument> documents = List.of(
                document("publisher-1", TaxonomyTargetType.PUBLISHER),
                document("author-1", TaxonomyTargetType.AUTHOR));

        listener.onTaxonomyIndex(TaxonomyIndexEvent.upsert(documents));

        verify(taxonomySuggestService, times(1)).index(documents);
        verifyNoMoreInteractions(taxonomySuggestService);
    }

    @Test
    @DisplayName("REMOVE -> TaxonomySuggestService.delete(documentId)")
    void remove_delegatesToDelete() {
        listener.onTaxonomyIndex(TaxonomyIndexEvent.remove("publisher-3"));

        verify(taxonomySuggestService).delete("publisher-3");
        verifyNoMoreInteractions(taxonomySuggestService);
    }

    @Test
    @DisplayName("awaria indeksu jest logowana i NIE wypływa z listenera — mutacja jest już scommitowana")
    void indexFailure_isLoggedAndSwallowed() {
        doThrow(new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE))
                .when(taxonomySuggestService).index(anyList());

        assertThatNoException().isThrownBy(() -> listener.onTaxonomyIndex(
                TaxonomyIndexEvent.upsert(document("publisher-1", TaxonomyTargetType.PUBLISHER))));
    }

    @Test
    @DisplayName("awaria usuwania też jest wyciszana")
    void deleteFailure_isLoggedAndSwallowed() {
        doThrow(new InfrastructureException(ErrorCode.SEARCH_FAILED))
                .when(taxonomySuggestService).delete("author-3");

        assertThatNoException()
                .isThrownBy(() -> listener.onTaxonomyIndex(TaxonomyIndexEvent.remove("author-3")));
    }
}
