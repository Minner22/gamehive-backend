package pl.m22.gamehive.game.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class GameSearchDocumentFactoryTest {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameExpansionRepository expansionRepository;

    private final GameSearchDocumentFactory factory = new GameSearchDocumentFactory();

    @Test
    @DisplayName("gra -> dokument 'game-{id}' z polami szukanymi i wszystkimi listami id słowników")
    void gameDocument_carriesSearchableAndFilterableFields() {
        Game agricola = gameRepository.findById(1L).orElseThrow();

        GameSearchDocument document = factory.toDocument(agricola);

        assertThat(document.id()).isEqualTo("game-1");
        assertThat(document.targetType()).isEqualTo(ContentModerationTargetType.GAME);
        assertThat(document.targetId()).isEqualTo(1L);
        assertThat(document.title()).isEqualTo("Agricola");
        assertThat(document.description()).isEqualTo("Klasyczna gra o rozwoju farmy.");
        assertThat(document.publisherIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(document.categoryIds()).containsExactly(1L);
        assertThat(document.mechanicIds()).containsExactly(1L);
        assertThat(document.authorIds()).containsExactly(1L);
        assertThat(document.minPlayers()).isEqualTo(1);
        assertThat(document.maxPlayers()).isEqualTo(4);
        assertThat(document.playingTimeMinutes()).isEqualTo(120);
        assertThat(document.yearPublished()).isEqualTo(2007);
        assertThat(document.minAge()).isEqualTo(12);
        // gra nie ma gry bazowej — pola dodatku puste (Gson pomija je w JSON-ie)
        assertThat(document.baseGameId()).isNull();
        assertThat(document.baseGameTitle()).isNull();
    }

    @Test
    @DisplayName("dodatek -> dokument z wartościami EFEKTYWNYMI (nadpisanie + dziedziczenie) i tytułem gry bazowej")
    void expansionDocument_usesEffectiveValues() {
        GameExpansion river = expansionRepository.findById(1L).orElseThrow();

        GameSearchDocument document = factory.toDocument(river);

        assertThat(document.id()).isEqualTo("expansion-1");
        assertThat(document.targetType()).isEqualTo(ContentModerationTargetType.EXPANSION);
        assertThat(document.targetId()).isEqualTo(1L);
        assertThat(document.title()).isEqualTo("Carcassonne: Rzeka");
        assertThat(document.baseGameId()).isEqualTo(7L);
        assertThat(document.baseGameTitle()).isEqualTo("Carcassonne");   // szukane obok nazwy dodatku
        assertThat(document.maxPlayers()).isEqualTo(6);                  // własne nadpisanie
        assertThat(document.minPlayers()).isEqualTo(2);                  // dziedziczone z gry 7
        assertThat(document.playingTimeMinutes()).isEqualTo(45);         // dziedziczone
        assertThat(document.minAge()).isEqualTo(8);                      // dziedziczone
        assertThat(document.categoryIds()).containsExactly(5L);          // własne (Expansion Only)
        assertThat(document.mechanicIds()).containsExactly(3L);          // dziedziczone (Area Control)
        // GH-120 nie daje dodatkowi wydawców, autorów ani roku wydania
        assertThat(document.publisherIds()).isEmpty();
        assertThat(document.authorIds()).isEmpty();
        assertThat(document.yearPublished()).isNull();
    }

    @Test
    @DisplayName("documentId() prefiksuje typem — gry i dodatki mają niezależne sekwencje id")
    void documentId_isPrefixedByTargetType() {
        assertThat(GameSearchDocumentFactory.documentId(ContentModerationTargetType.GAME, 1L))
                .isEqualTo("game-1");
        assertThat(GameSearchDocumentFactory.documentId(ContentModerationTargetType.EXPANSION, 1L))
                .isEqualTo("expansion-1");
    }
}
