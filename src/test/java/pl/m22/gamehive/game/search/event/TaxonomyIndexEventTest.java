package pl.m22.gamehive.game.search.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.model.TaxonomyTargetType;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxonomyIndexEventTest {

    private static TaxonomyDocument publisher(String id) {
        return new TaxonomyDocument(id, TaxonomyTargetType.PUBLISHER, 1L, "Rio Grande Games",
                TaxonomyStatus.APPROVED);
    }

    private static TaxonomyDocument author(String id) {
        return new TaxonomyDocument(id, TaxonomyTargetType.AUTHOR, 1L, "Uwe Rosenberg", TaxonomyStatus.PENDING);
    }

    @Test
    @DisplayName("describeTargets() dla UPSERT wylicza id całej partii — to jedyna diagnostyka przy rozjeździe")
    void describeTargets_forUpsert_listsEveryDocumentId() {
        TaxonomyIndexEvent event = TaxonomyIndexEvent.upsert(List.of(publisher("publisher-1"), author("author-1")));

        assertThat(event.describeTargets()).isEqualTo("publisher-1, author-1");
    }

    @Test
    @DisplayName("describeTargets() dla REMOVE zwraca pojedyncze id dokumentu")
    void describeTargets_forRemove_returnsDocumentId() {
        assertThat(TaxonomyIndexEvent.remove("author-3").describeTargets()).isEqualTo("author-3");
    }

    @Test
    @DisplayName("upsert(document) to skrót na jednoelementową partię")
    void upsert_singleDocument_wrapsIntoBatch() {
        TaxonomyIndexEvent event = TaxonomyIndexEvent.upsert(publisher("publisher-3"));

        assertThat(event.operation()).isEqualTo(SearchIndexOperation.UPSERT);
        assertThat(event.documents()).singleElement().extracting(TaxonomyDocument::id).isEqualTo("publisher-3");
        assertThat(event.documentId()).isNull();
    }

    @Test
    @DisplayName("lista dokumentów jest kopiowana i niemodyfikowalna — zdarzenie przechodzi na wątek roboczy")
    void documents_areDefensivelyCopied() {
        List<TaxonomyDocument> source = new ArrayList<>(List.of(publisher("publisher-1")));

        TaxonomyIndexEvent event = TaxonomyIndexEvent.upsert(source);
        source.add(publisher("publisher-2"));

        assertThat(event.documents()).hasSize(1);
        assertThatThrownBy(() -> event.documents().add(publisher("publisher-3")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("REMOVE bez id dokumentu jest odrzucane w konstruktorze, a nie cicho gubione w SDK")
    void remove_withoutDocumentId_isRejected() {
        assertThatThrownBy(() -> new TaxonomyIndexEvent(SearchIndexOperation.REMOVE, List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("documentId");
    }

    @Test
    @DisplayName("brak operacji jest odrzucany w konstruktorze")
    void operation_isRequired() {
        assertThatThrownBy(() -> new TaxonomyIndexEvent(null, List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operation");
    }
}
