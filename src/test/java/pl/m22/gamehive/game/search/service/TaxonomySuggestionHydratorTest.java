package pl.m22.gamehive.game.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.game.dto.AuthorDto;
import pl.m22.gamehive.game.dto.PublisherDto;
import pl.m22.gamehive.game.model.TaxonomyStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Kontrakt hydracji podpowiedzi. Meili oddaje wyłącznie identyfikatory W KOLEJNOŚCI RANKINGU, więc to
 * tutaj rozstrzyga się, czy ranking dotrwa do odpowiedzi — asercje kolejności są sensem tej klasy.
 * Różnica wobec {@code SearchResultHydratorTest}: tam trafienie spoza APPROVED jest odrzucane,
 * tu odrzucamy WYŁĄCZNIE wiersze, których już nie ma.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaxonomySuggestionHydratorTest {

    @Autowired
    TaxonomySuggestionHydrator hydrator;

    @MockitoBean
    JavaMailSender mailSender;

    @Test
    @DisplayName("wydawcy wracają w kolejności identyfikatorów z rankingu, a nie w kolejności bazy")
    void hydratePublishers_keepsRankingOrderNotDatabaseOrder() {
        assertThat(hydrator.hydratePublishers(List.of(3L, 1L, 2L)))
                .extracting(PublisherDto::name)
                .containsExactly("Pending Games", "Rio Grande Games", "Z-Man Games");

        // ta sama trójka w odwrotnej kolejności — gdyby hydrator mapował wynik findAllById,
        // oba wywołania oddałyby identyczną listę i pierwsza asercja przechodziłaby przypadkiem
        assertThat(hydrator.hydratePublishers(List.of(2L, 1L, 3L)))
                .extracting(PublisherDto::name)
                .containsExactly("Z-Man Games", "Rio Grande Games", "Pending Games");
    }

    @Test
    @DisplayName("autorzy też zachowują kolejność rankingu, z nazwą złożoną z imienia i nazwiska")
    void hydrateAuthors_keepsRankingOrder() {
        assertThat(hydrator.hydrateAuthors(List.of(2L, 1L)))
                .extracting(AuthorDto::firstName, AuthorDto::lastName)
                .containsExactly(tuple("Reiner", "Knizia"), tuple("Uwe", "Rosenberg"));
    }

    @Test
    @DisplayName("podpowiedzi obejmują KAŻDY status — hydrator nie filtruje, w odróżnieniu od #122")
    void hydratePublishers_doesNotFilterByStatus() {
        assertThat(hydrator.hydratePublishers(List.of(3L)))
                .singleElement()
                .satisfies(publisher -> {
                    assertThat(publisher.name()).isEqualTo("Pending Games");
                    assertThat(publisher.status()).isEqualTo(TaxonomyStatus.PENDING);
                });

        assertThat(hydrator.hydrateAuthors(List.of(3L)))
                .singleElement()
                .extracting(AuthorDto::status)
                .isEqualTo(TaxonomyStatus.PENDING);
    }

    @Test
    @DisplayName("id skasowanego wiersza jest pomijane, nie wstawia null-a — nieświeży indeks sam się leczy")
    void hydrate_skipsIdsWithoutRow() {
        assertThat(hydrator.hydratePublishers(List.of(999L, 1L, 998L)))
                .extracting(PublisherDto::name)
                .containsExactly("Rio Grande Games");

        assertThat(hydrator.hydrateAuthors(List.of(999L, 1L))).hasSize(1);
        assertThat(hydrator.hydratePublishers(List.of(999L))).isEmpty();
    }

    @Test
    @DisplayName("powtórzone id daje JEDNĄ podpowiedź (symetria z SearchResultHydrator)")
    void hydrate_deduplicatesRepeatedIds() {
        assertThat(hydrator.hydratePublishers(List.of(1L, 1L, 2L, 1L)))
                .extracting(PublisherDto::name)
                .containsExactly("Rio Grande Games", "Z-Man Games");
    }

    @Test
    @DisplayName("brak trafień -> pusta lista bez odpytywania bazy")
    void hydrate_emptyIds_returnsEmptyList() {
        assertThat(hydrator.hydratePublishers(List.of())).isEmpty();
        assertThat(hydrator.hydrateAuthors(List.of())).isEmpty();
    }
}
