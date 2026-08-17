package pl.m22.gamehive.game.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.m22.gamehive.game.model.Author;
import pl.m22.gamehive.game.model.Publisher;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.model.TaxonomyTargetType;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;

import static org.assertj.core.api.Assertions.assertThat;

class TaxonomyDocumentFactoryTest {

    private final TaxonomyDocumentFactory factory = new TaxonomyDocumentFactory();

    @Test
    @DisplayName("dokument wydawcy -> id 'publisher-{id}', nazwa i status bez zmian")
    void toDocument_publisher() {
        TaxonomyDocument document = factory.toDocument(
                withId(Publisher.of("Pending Games", TaxonomyStatus.PENDING), 3L));

        assertThat(document.id()).isEqualTo("publisher-3");
        assertThat(document.targetType()).isEqualTo(TaxonomyTargetType.PUBLISHER);
        assertThat(document.targetId()).isEqualTo(3L);
        assertThat(document.name()).isEqualTo("Pending Games");
        assertThat(document.status()).isEqualTo(TaxonomyStatus.PENDING);
    }

    @Test
    @DisplayName("dokument autora -> id 'author-{id}', nazwa to 'imię nazwisko' w jednym polu szukanym")
    void toDocument_author() {
        TaxonomyDocument document = factory.toDocument(
                withId(Author.of("Uwe", "Rosenberg", TaxonomyStatus.APPROVED), 1L));

        assertThat(document.id()).isEqualTo("author-1");
        assertThat(document.targetType()).isEqualTo(TaxonomyTargetType.AUTHOR);
        assertThat(document.targetId()).isEqualTo(1L);
        assertThat(document.name()).isEqualTo("Uwe Rosenberg");
        assertThat(document.status()).isEqualTo(TaxonomyStatus.APPROVED);
    }

    @Test
    @DisplayName("documentId jest statyczne — klucz usunięcia da się zbudować bez ładowania encji")
    void documentId_isStaticAndPrefixed() {
        assertThat(TaxonomyDocumentFactory.documentId(TaxonomyTargetType.PUBLISHER, 7L)).isEqualTo("publisher-7");
        assertThat(TaxonomyDocumentFactory.documentId(TaxonomyTargetType.AUTHOR, 7L)).isEqualTo("author-7");
    }

    // id nadaje IDENTITY dopiero przy zapisie, a tu nie ma bazy
    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
