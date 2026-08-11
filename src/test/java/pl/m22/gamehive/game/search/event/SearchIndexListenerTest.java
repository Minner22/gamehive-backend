package pl.m22.gamehive.game.search.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;
import pl.m22.gamehive.game.search.service.GameSearchService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchIndexListenerTest {

    @Mock GameSearchService gameSearchService;
    @InjectMocks SearchIndexListener listener;

    private static GameSearchDocument document() {
        return new GameSearchDocument("game-1", ContentModerationTargetType.GAME, 1L,
                "Agricola", "Opis.", null, List.of(1L), List.of(1L), List.of(), List.of(),
                1, 4, 120, 2007, 12, null);
    }

    @Test
    @DisplayName("UPSERT -> GameSearchService.index(dokument)")
    void upsert_delegatesToIndex() {
        GameSearchDocument document = document();

        listener.onSearchIndex(SearchIndexEvent.upsert(document));

        verify(gameSearchService).index(document);
        verifyNoMoreInteractions(gameSearchService);
    }

    @Test
    @DisplayName("REMOVE -> GameSearchService.delete(documentId)")
    void remove_delegatesToDelete() {
        listener.onSearchIndex(SearchIndexEvent.remove("expansion-1"));

        verify(gameSearchService).delete("expansion-1");
        verifyNoMoreInteractions(gameSearchService);
    }

    @Test
    @DisplayName("awaria indeksu jest logowana i NIE wypływa z listenera — akcja biznesowa jest już scommitowana")
    void indexFailure_isLoggedAndSwallowed() {
        doThrow(new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE))
                .when(gameSearchService).index(any());

        assertThatNoException()
                .isThrownBy(() -> listener.onSearchIndex(SearchIndexEvent.upsert(document())));
    }

    @Test
    @DisplayName("awaria usuwania też jest wyciszana")
    void deleteFailure_isLoggedAndSwallowed() {
        doThrow(new InfrastructureException(ErrorCode.SEARCH_FAILED))
                .when(gameSearchService).delete("game-1");

        assertThatNoException()
                .isThrownBy(() -> listener.onSearchIndex(SearchIndexEvent.remove("game-1")));
    }
}
