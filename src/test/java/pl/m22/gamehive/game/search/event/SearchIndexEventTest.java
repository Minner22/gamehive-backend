package pl.m22.gamehive.game.search.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchIndexEventTest {

    private static GameSearchDocument document(String id) {
        return new GameSearchDocument(id, ContentModerationTargetType.GAME, 1L,
                "Agricola", "Opis.", null, List.of(1L), List.of(1L), List.of(), List.of(),
                1, 4, 120, 2007, 12, null);
    }

    @Test
    @DisplayName("describeTargets() dla UPSERT wylicza id wszystkich dokumentów partii")
    void describeTargets_forUpsert_listsEveryDocumentId() {
        SearchIndexEvent event = SearchIndexEvent.upsert(List.of(document("game-1"), document("expansion-2")));

        assertThat(event.describeTargets()).isEqualTo("game-1, expansion-2");
    }

    @Test
    @DisplayName("describeTargets() dla REMOVE zwraca pojedyncze id dokumentu")
    void describeTargets_forRemove_returnsDocumentId() {
        assertThat(SearchIndexEvent.remove("expansion-1").describeTargets()).isEqualTo("expansion-1");
    }

    @Test
    @DisplayName("lista dokumentów jest kopiowana i niemodyfikowalna — zdarzenie przechodzi na wątek roboczy")
    void documents_areDefensivelyCopied() {
        List<GameSearchDocument> source = new ArrayList<>(List.of(document("game-1")));

        SearchIndexEvent event = SearchIndexEvent.upsert(source);
        source.add(document("game-2"));

        assertThat(event.documents()).hasSize(1);
        assertThatThrownBy(() -> event.documents().add(document("game-3")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("REMOVE bez identyfikatora dokumentu jest odrzucane już w konstruktorze")
    void remove_withoutDocumentId_isRejected() {
        assertThatThrownBy(() -> new SearchIndexEvent(SearchIndexOperation.REMOVE, List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
