package pl.m22.gamehive.game.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.model.TaxonomyTargetType;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TaxonomyDocumentReaderTest {

    @Autowired TaxonomyDocumentReader documentReader;
    @MockitoBean JavaMailSender mailSender;

    @Test
    @DisplayName("readPublishers oddaje WSZYSTKIE statusy — inaczej niż ApprovedContentDocumentReader")
    void readPublishers_includesPending() {
        Page<TaxonomyDocument> page = documentReader.readPublishers(PageRequest.of(0, 100, Sort.by("id")));

        assertThat(page.getContent()).extracting(TaxonomyDocument::status)
                .contains(TaxonomyStatus.APPROVED, TaxonomyStatus.PENDING);
        assertThat(page.getContent()).extracting(TaxonomyDocument::id).contains("publisher-3");
        assertThat(page.getContent()).extracting(TaxonomyDocument::targetType)
                .containsOnly(TaxonomyTargetType.PUBLISHER);
    }

    @Test
    @DisplayName("readAuthors buduje nazwę z imienia i nazwiska oraz stronicuje (reindeks partiami)")
    void readAuthors_buildsNameAndPages() {
        Page<TaxonomyDocument> firstPage = documentReader.readAuthors(PageRequest.of(0, 1, Sort.by("id")));

        assertThat(firstPage.getContent()).singleElement().satisfies(document -> {
            assertThat(document.id()).isEqualTo("author-1");
            assertThat(document.name()).isEqualTo("Uwe Rosenberg");
            assertThat(document.targetType()).isEqualTo(TaxonomyTargetType.AUTHOR);
        });
        assertThat(firstPage.hasNext()).isTrue();
    }

    @Test
    @DisplayName("readAuthors oddaje też autora PENDING")
    void readAuthors_includesPending() {
        Page<TaxonomyDocument> page = documentReader.readAuthors(PageRequest.of(0, 100, Sort.by("id")));

        assertThat(page.getContent())
                .filteredOn(document -> document.status() == TaxonomyStatus.PENDING)
                .extracting(TaxonomyDocument::name)
                .contains("Oczekujacy Autor");
    }
}
