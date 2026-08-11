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
import pl.m22.gamehive.game.search.dto.GameSearchDocument;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApprovedContentDocumentReaderTest {

    @Autowired ApprovedContentDocumentReader reader;
    @MockitoBean JavaMailSender mailSender;

    @Test
    @DisplayName("czyta wyłącznie APPROVED gry (Agricola + Carcassonne), stronicowo")
    void readGames_returnsOnlyApprovedDocumentsPaged() {
        Page<GameSearchDocument> firstPage = reader.readGames(PageRequest.of(0, 1, Sort.by("id")));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);   // z fixtur tylko gry 1 i 7 są APPROVED
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.getContent()).extracting(GameSearchDocument::id).containsExactly("game-1");

        Page<GameSearchDocument> secondPage = reader.readGames(PageRequest.of(1, 1, Sort.by("id")));

        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.getContent()).extracting(GameSearchDocument::id).containsExactly("game-7");
    }

    @Test
    @DisplayName("strona za końcem zbioru jest pusta (warunek wyjścia z pętli reindeksu)")
    void readGames_pastLastPage_isEmpty() {
        assertThat(reader.readGames(PageRequest.of(5, 10, Sort.by("id")))).isEmpty();
    }

    @Test
    @DisplayName("czyta wyłącznie APPROVED dodatki, z wartościami efektywnymi (LAZY gra bazowa w tej samej tx)")
    void readExpansions_returnsOnlyApprovedDocuments() {
        Page<GameSearchDocument> page = reader.readExpansions(PageRequest.of(0, 50, Sort.by("id")));

        assertThat(page.getContent()).extracting(GameSearchDocument::id).containsExactly("expansion-1");
        GameSearchDocument document = page.getContent().getFirst();
        assertThat(document.baseGameTitle()).isEqualTo("Carcassonne");
        assertThat(document.maxPlayers()).isEqualTo(6);   // własne nadpisanie
        assertThat(document.minAge()).isEqualTo(8);       // dziedziczone z gry bazowej
        assertThat(document.categoryIds()).containsExactly(5L);
    }
}
