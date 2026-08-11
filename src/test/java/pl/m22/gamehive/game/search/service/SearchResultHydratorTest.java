package pl.m22.gamehive.game.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.dto.SearchHitRef;
import pl.m22.gamehive.game.search.dto.SearchResultDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SearchResultHydratorTest {

    @Autowired
    SearchResultHydrator hydrator;

    @MockitoBean
    JavaMailSender mailSender;

    @Test
    @DisplayName("hydracja zachowuje kolejność rankingu Meili i mapuje na DTO biblioteki")
    void hydrate_keepsMeiliOrderAndMapsLibraryDtos() {
        List<SearchHitRef> hits = List.of(
                new SearchHitRef(ContentModerationTargetType.EXPANSION, 1L),
                new SearchHitRef(ContentModerationTargetType.GAME, 7L),
                new SearchHitRef(ContentModerationTargetType.GAME, 1L));

        List<SearchResultDto> results = hydrator.hydrate(hits);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).targetType()).isEqualTo(ContentModerationTargetType.EXPANSION);
        assertThat(results.get(0).expansion().name()).isEqualTo("Carcassonne: Rzeka");
        assertThat(results.get(0).expansion().effectiveMaxPlayers()).isEqualTo(6);
        assertThat(results.get(0).game()).isNull();
        assertThat(results.get(1).game().title()).isEqualTo("Carcassonne");
        assertThat(results.get(1).expansion()).isNull();
        assertThat(results.get(2).game().title()).isEqualTo("Agricola");
    }

    @Test
    @DisplayName("trafienie nieistniejące i nie-APPROVED jest pomijane (indeks to podpowiedź, baza to prawda)")
    void hydrate_skipsStaleHits() {
        List<SearchHitRef> hits = List.of(
                new SearchHitRef(ContentModerationTargetType.GAME, 2L),         // Pandemic — PENDING
                new SearchHitRef(ContentModerationTargetType.GAME, 999L),       // nie istnieje
                new SearchHitRef(ContentModerationTargetType.EXPANSION, 2L),    // dodatek PENDING
                new SearchHitRef(ContentModerationTargetType.EXPANSION, 999L),  // nie istnieje
                new SearchHitRef(ContentModerationTargetType.GAME, 1L));        // Agricola — APPROVED

        assertThat(hydrator.hydrate(hits))
                .singleElement()
                .satisfies(result -> assertThat(result.game().title()).isEqualTo("Agricola"));
    }

    @Test
    @DisplayName("brak trafień -> pusta lista")
    void hydrate_emptyHits_returnsEmptyList() {
        assertThat(hydrator.hydrate(List.of())).isEmpty();
    }
}
