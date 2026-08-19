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
import pl.m22.gamehive.game.model.Publisher;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.repository.PublisherRepository;
import pl.m22.gamehive.game.search.dto.TaxonomyReindexCounts;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Fallback bazodanowy jest w profilu test JEDYNĄ implementacją, więc — inaczej niż w #122 — suita realnie
 * sprawdza wyniki podpowiedzi, a nie tylko to, że endpoint nie wywala 5xx.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DatabaseTaxonomySuggestServiceTest {

    @Autowired TaxonomySuggestService taxonomySuggestService;
    @Autowired PublisherRepository publisherRepository;
    @MockitoBean JavaMailSender mailSender;

    @Test
    @DisplayName("w profilu test aktywny jest fallback bazodanowy, nie no-op")
    void testProfile_activatesDatabaseFallback() {
        assertThat(taxonomySuggestService).isInstanceOf(DatabaseTaxonomySuggestService.class);
    }

    @Test
    @DisplayName("suggestPublishers po fragmencie nazwy, bez względu na wielkość liter")
    void suggestPublishers_matchesNameFragmentIgnoringCase() {
        assertThat(taxonomySuggestService.suggestPublishers("GRANDE", 10))
                .extracting(PublisherDto::name)
                .containsExactly("Rio Grande Games");
    }

    @Test
    @DisplayName("suggestPublishers pokazuje wpisy PENDING (różnica wobec #122), a DTO nosi status")
    void suggestPublishers_includesPendingWithStatus() {
        assertThat(taxonomySuggestService.suggestPublishers("pending", 10))
                .singleElement()
                .satisfies(publisher -> {
                    assertThat(publisher.name()).isEqualTo("Pending Games");
                    assertThat(publisher.status()).isEqualTo(TaxonomyStatus.PENDING);
                    assertThat(publisher.id()).isNotNull();
                });
    }

    @Test
    @DisplayName("suggestPublishers respektuje limit — 'Games' pasuje do wszystkich trzech zasianych wydawców")
    void suggestPublishers_respectsLimit() {
        assertThat(taxonomySuggestService.suggestPublishers("games", 10)).hasSizeGreaterThanOrEqualTo(3);
        assertThat(taxonomySuggestService.suggestPublishers("games", 2)).hasSize(2);
        assertThat(taxonomySuggestService.suggestPublishers("games", 1)).hasSize(1);
    }

    @Test
    @DisplayName("suggestPublishers sortuje alfabetycznie — fallback nie ma rankingu trafności")
    void suggestPublishers_sortsByName() {
        assertThat(taxonomySuggestService.suggestPublishers("games", 3))
                .extracting(PublisherDto::name)
                .containsExactly("Pending Games", "Rio Grande Games", "Z-Man Games");
    }

    @Test
    @DisplayName("brak dopasowania -> pusta lista, nie wyjątek")
    void suggestPublishers_noMatch_returnsEmpty() {
        assertThat(taxonomySuggestService.suggestPublishers("nieistniejacy wydawca", 10)).isEmpty();
    }

    @Test
    @DisplayName("suggestAuthors dopasowuje po imieniu")
    void suggestAuthors_matchesFirstName() {
        assertThat(taxonomySuggestService.suggestAuthors("uwe", 10))
                .extracting(AuthorDto::firstName, AuthorDto::lastName)
                .containsExactly(tuple("Uwe", "Rosenberg"));
    }

    @Test
    @DisplayName("suggestAuthors dopasowuje po nazwisku")
    void suggestAuthors_matchesLastName() {
        assertThat(taxonomySuggestService.suggestAuthors("knizia", 10))
                .extracting(AuthorDto::firstName)
                .containsExactly("Reiner");
    }

    @Test
    @DisplayName("suggestAuthors dopasowuje po pełnej frazie 'Imię Nazwisko' (konkatenacja, nie dwa OR-y)")
    void suggestAuthors_matchesFullName() {
        assertThat(taxonomySuggestService.suggestAuthors("uwe rosen", 10))
                .extracting(AuthorDto::lastName)
                .containsExactly("Rosenberg");
    }

    @Test
    @DisplayName("suggestAuthors pokazuje autora PENDING ze statusem")
    void suggestAuthors_includesPending() {
        assertThat(taxonomySuggestService.suggestAuthors("oczekujacy", 10))
                .singleElement()
                .extracting(AuthorDto::status)
                .isEqualTo(TaxonomyStatus.PENDING);
    }

    @Test
    @DisplayName("suggestAuthors sortuje po nazwisku, potem imieniu")
    void suggestAuthors_sortsByLastNameThenFirstName() {
        assertThat(taxonomySuggestService.suggestAuthors(null, 3))
                .extracting(AuthorDto::lastName)
                .containsExactly("Autor", "Knizia", "Rosenberg");
    }

    @Test
    @DisplayName("pusta lub brakująca fraza -> początek listy (przeglądanie), nie wyjątek")
    void suggest_blankQuery_returnsPrefixOfList() {
        assertThat(taxonomySuggestService.suggestPublishers(null, 2)).hasSize(2);
        assertThat(taxonomySuggestService.suggestPublishers("   ", 2)).hasSize(2);
        assertThat(taxonomySuggestService.suggestAuthors("", 2)).hasSize(2);
    }

    @Test
    @DisplayName("wildcardy LIKE we frazie są ekranowane — '%' nie dopasowuje całej tabeli")
    void suggest_escapesLikeWildcards() {
        assertThat(taxonomySuggestService.suggestPublishers("%", 10)).isEmpty();
        assertThat(taxonomySuggestService.suggestPublishers("_", 10)).isEmpty();
        assertThat(taxonomySuggestService.suggestPublishers("%games%", 10)).isEmpty();
        assertThat(taxonomySuggestService.suggestAuthors("_", 10)).isEmpty();
    }

    @Test
    @DisplayName("znak ucieczki jest podwajany JAKO PIERWSZY — fraza z backslashem dopasowuje się literalnie")
    void suggest_escapesTheEscapeCharacterItself() {
        // fixtures nie mają backslasha w nazwie, a bez tego wiersza mutacja usuwająca podwajanie
        // przechodziłaby niezauważona: wzorzec "%\\%" też zwraca pusty wynik. Klasa jest
        // @Transactional, więc wiersz nie wychodzi poza test.
        publisherRepository.saveAndFlush(Publisher.of("Back\\Slash Games", TaxonomyStatus.APPROVED));

        assertThat(taxonomySuggestService.suggestPublishers("back\\slash", 10))
                .extracting(PublisherDto::name)
                .containsExactly("Back\\Slash Games");
    }

    @Test
    @DisplayName("fraza jest trymowana — spacje wokół nie psują dopasowania")
    void suggest_trimsQuery() {
        assertThat(taxonomySuggestService.suggestPublishers("  grande  ", 10))
                .extracting(PublisherDto::name)
                .containsExactly("Rio Grande Games");
    }

    @Test
    @DisplayName("zapis do indeksu na fallbacku to no-op, nie wyjątek — nie ma indeksu, do którego pisać")
    void writeOperations_areNoOps() {
        assertThatNoException().isThrownBy(() -> {
            taxonomySuggestService.index(List.of());
            taxonomySuggestService.delete("publisher-1");
        });
        assertThat(taxonomySuggestService.reindexAll()).isEqualTo(new TaxonomyReindexCounts(0, 0));
    }
}
