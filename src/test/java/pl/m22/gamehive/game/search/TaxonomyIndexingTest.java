package pl.m22.gamehive.game.search;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.dto.AuthorRequestDto;
import pl.m22.gamehive.game.dto.GameRequestDto;
import pl.m22.gamehive.game.model.Author;
import pl.m22.gamehive.game.model.Publisher;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.repository.AuthorRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;
import pl.m22.gamehive.game.search.dto.TaxonomyDocument;
import pl.m22.gamehive.game.search.service.TaxonomySuggestService;
import pl.m22.gamehive.game.service.GameModerationService;
import pl.m22.gamehive.game.service.GameSubmissionService;
import pl.m22.gamehive.game.service.TaxonomyService;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Dowód wpięcia zdarzeń indeksujących taksonomię. Klasa jest NIE-@Transactional, bo listener działa
 * AFTER_COMMIT — i dlatego, zgodnie z regułą z GH-118/#121/#122, tworzy oraz kasuje WŁASNE wiersze.
 */
@SpringBootTest
@ActiveProfiles("test")
class TaxonomyIndexingTest {

    private static final Email MODERATOR = new Email("mark.moderator@example.com");
    private static final Email JANE = new Email("jane.smith@example.com");

    private static final List<String> OWN_PUBLISHERS = List.of(
            "Indeksowany Wydawca", "Do Zatwierdzenia", "Nowy Wydawca W Locie", "Kaskadowy Wydawca",
            "Wydawca Mimo Awarii", "Wydawca Do Usuniecia");

    private static final List<String[]> OWN_AUTHORS = List.of(
            new String[]{"Nowy", "AutorWLocie"}, new String[]{"Stare", "Nazwisko"}, new String[]{"Nowe", "Imie"},
            new String[]{"Do", "Usuniecia"}, new String[]{"Zaindeksowany", "Autor"},
            new String[]{"Do", "Zatwierdzenia"});

    @Autowired TaxonomyService taxonomyService;
    @Autowired GameSubmissionService gameSubmissionService;
    @Autowired GameModerationService gameModerationService;
    @Autowired PublisherRepository publisherRepository;
    @Autowired AuthorRepository authorRepository;
    @Autowired GameRepository gameRepository;
    @Autowired PlatformTransactionManager txManager;

    // fallback podmieniony mockiem — sprawdzamy WPIĘCIE zdarzeń, nie samo Meili
    @MockitoBean TaxonomySuggestService taxonomySuggestService;
    @MockitoBean JavaMailSender mailSender;

    private TransactionTemplate tx;

    private final List<Long> createdGameIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
    }

    @AfterEach
    void cleanup() {
        tx.executeWithoutResult(_ -> {
            createdGameIds.reversed().stream().filter(gameRepository::existsById).forEach(gameRepository::deleteById);
            OWN_PUBLISHERS.forEach(name ->
                    publisherRepository.findByName(name).ifPresent(publisherRepository::delete));
            OWN_AUTHORS.forEach(name -> authorRepository
                    .findByFirstNameAndLastName(name[0], name[1]).ifPresent(authorRepository::delete));
        });
        createdGameIds.clear();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<TaxonomyDocument>> documentBatchCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private static GameRequestDto gameRequest(String title,
                                              List<String> newPublisherNames,
                                              List<AuthorRequestDto> newAuthors,
                                              boolean submit) {
        return new GameRequestDto(title, "Opis testowy.", 2, 4, 60, 2024, 10, null,
                List.of(), newPublisherNames, List.of(1L), List.of(), List.of(), newAuthors, submit);
    }

    // ---------- wydawcy (admin) ----------

    @Test
    @DisplayName("admin tworzy wydawcę -> upsert 'publisher-{id}' ze statusem APPROVED")
    void createPublisher_indexesDocument() {
        Long id = tx.execute(_ -> taxonomyService.createPublisher("Indeksowany Wydawca").getId());

        ArgumentCaptor<List<TaxonomyDocument>> batch = documentBatchCaptor();
        verify(taxonomySuggestService).index(batch.capture());
        assertThat(batch.getValue()).singleElement().satisfies(document -> {
            assertThat(document.id()).isEqualTo("publisher-" + id);
            assertThat(document.name()).isEqualTo("Indeksowany Wydawca");
            assertThat(document.status()).isEqualTo(TaxonomyStatus.APPROVED);
        });
        verify(taxonomySuggestService, never()).delete(any());
    }

    @Test
    @DisplayName("approve wydawcy -> upsert dokumentu ze statusem APPROVED")
    void approvePublisher_indexesApprovedStatus() {
        Long id = tx.execute(_ ->
                publisherRepository.save(Publisher.of("Do Zatwierdzenia", TaxonomyStatus.PENDING)).getId());
        clearInvocations(taxonomySuggestService);

        tx.executeWithoutResult(_ -> taxonomyService.approvePublisher(id));

        ArgumentCaptor<List<TaxonomyDocument>> batch = documentBatchCaptor();
        verify(taxonomySuggestService).index(batch.capture());
        assertThat(batch.getValue()).singleElement().satisfies(document -> {
            assertThat(document.id()).isEqualTo("publisher-" + id);
            assertThat(document.status()).isEqualTo(TaxonomyStatus.APPROVED);
        });
    }

    @Test
    @DisplayName("approve już zatwierdzonego wydawcy też publikuje — upsert jest idempotentny i naprawia indeks")
    void approvePublisher_whenAlreadyApproved_stillIndexes() {
        tx.executeWithoutResult(_ -> taxonomyService.approvePublisher(1L));

        ArgumentCaptor<List<TaxonomyDocument>> batch = documentBatchCaptor();
        verify(taxonomySuggestService).index(batch.capture());
        assertThat(batch.getValue()).singleElement()
                .extracting(TaxonomyDocument::id).isEqualTo("publisher-1");
    }

    @Test
    @DisplayName("usunięcie wydawcy -> delete 'publisher-{id}', bez upsertu")
    void deletePublisher_removesDocument() {
        Long id = tx.execute(_ ->
                publisherRepository.save(Publisher.of("Wydawca Do Usuniecia", TaxonomyStatus.APPROVED)).getId());
        clearInvocations(taxonomySuggestService);

        tx.executeWithoutResult(_ -> taxonomyService.deletePublisher(id));

        verify(taxonomySuggestService).delete("publisher-" + id);
        verify(taxonomySuggestService, never()).index(any());
    }

    @Test
    @DisplayName("odrzucone usunięcie wydawcy (PUBLISHER_IN_USE) -> zero ruchu w indeksie")
    void deletePublisher_whenInUse_doesNotTouchIndex() {
        assertThatThrownBy(() -> tx.executeWithoutResult(_ -> taxonomyService.deletePublisher(1L)))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PUBLISHER_IN_USE);

        verifyNoInteractions(taxonomySuggestService);
    }

    // ---------- autorzy (admin) ----------

    @Test
    @DisplayName("admin tworzy autora -> upsert 'author-{id}' z nazwą 'imię nazwisko'")
    void createAuthor_indexesDocument() {
        Long id = tx.execute(_ -> taxonomyService.createAuthor("Zaindeksowany", "Autor").getId());

        ArgumentCaptor<List<TaxonomyDocument>> batch = documentBatchCaptor();
        verify(taxonomySuggestService).index(batch.capture());
        assertThat(batch.getValue()).singleElement().satisfies(document -> {
            assertThat(document.id()).isEqualTo("author-" + id);
            assertThat(document.name()).isEqualTo("Zaindeksowany Autor");
            assertThat(document.status()).isEqualTo(TaxonomyStatus.APPROVED);
        });
    }

    @Test
    @DisplayName("approve autora -> upsert dokumentu ze statusem APPROVED (symetrycznie do wydawcy)")
    void approveAuthor_indexesApprovedStatus() {
        Long id = tx.execute(_ ->
                authorRepository.save(Author.of("Do", "Zatwierdzenia", TaxonomyStatus.PENDING)).getId());
        clearInvocations(taxonomySuggestService);

        tx.executeWithoutResult(_ -> taxonomyService.approveAuthor(id));

        ArgumentCaptor<List<TaxonomyDocument>> batch = documentBatchCaptor();
        verify(taxonomySuggestService).index(batch.capture());
        assertThat(batch.getValue()).singleElement().satisfies(document -> {
            assertThat(document.id()).isEqualTo("author-" + id);
            assertThat(document.name()).isEqualTo("Do Zatwierdzenia");
            assertThat(document.status()).isEqualTo(TaxonomyStatus.APPROVED);
        });
    }

    @Test
    @DisplayName("zmiana nazwy autora -> upsert z NOWĄ nazwą (bez tego podpowiedź zostałaby ze starą)")
    void updateAuthor_indexesNewName() {
        Long id = tx.execute(_ ->
                authorRepository.save(Author.of("Stare", "Nazwisko", TaxonomyStatus.APPROVED)).getId());
        clearInvocations(taxonomySuggestService);

        tx.executeWithoutResult(_ -> taxonomyService.updateAuthor(id, "Nowe", "Imie"));

        ArgumentCaptor<List<TaxonomyDocument>> batch = documentBatchCaptor();
        verify(taxonomySuggestService).index(batch.capture());
        assertThat(batch.getValue()).singleElement()
                .extracting(TaxonomyDocument::name).isEqualTo("Nowe Imie");
    }

    @Test
    @DisplayName("usunięcie autora -> delete 'author-{id}'")
    void deleteAuthor_removesDocument() {
        Long id = tx.execute(_ ->
                authorRepository.save(Author.of("Do", "Usuniecia", TaxonomyStatus.APPROVED)).getId());
        clearInvocations(taxonomySuggestService);

        tx.executeWithoutResult(_ -> taxonomyService.deleteAuthor(id));

        verify(taxonomySuggestService).delete("author-" + id);
        verify(taxonomySuggestService, never()).index(any());
    }

    @Test
    @DisplayName("odrzucone usunięcie autora (AUTHOR_IN_USE) -> zero ruchu w indeksie")
    void deleteAuthor_whenInUse_doesNotTouchIndex() {
        assertThatThrownBy(() -> tx.executeWithoutResult(_ -> taxonomyService.deleteAuthor(1L)))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTHOR_IN_USE);

        verifyNoInteractions(taxonomySuggestService);
    }

    // ---------- tworzenie w locie przy zgłoszeniu gry ----------

    @Test
    @DisplayName("wydawca i autor utworzeni w locie -> JEDEN upsert z obydwoma, status PENDING")
    void inFlightCreation_indexesBothInOneBatch() {
        Long gameId = tx.execute(_ -> gameSubmissionService.createGame(
                gameRequest("Gra z nowa taksonomia", List.of("Nowy Wydawca W Locie"),
                        List.of(new AuthorRequestDto("Nowy", "AutorWLocie")), false),
                JANE).id());
        createdGameIds.add(gameId);

        ArgumentCaptor<List<TaxonomyDocument>> batch = documentBatchCaptor();
        verify(taxonomySuggestService, times(1)).index(batch.capture());
        assertThat(batch.getValue()).hasSize(2)
                .extracting(TaxonomyDocument::name, TaxonomyDocument::status)
                .containsExactlyInAnyOrder(
                        tuple("Nowy Wydawca W Locie", TaxonomyStatus.PENDING),
                        tuple("Nowy AutorWLocie", TaxonomyStatus.PENDING));
    }

    @Test
    @DisplayName("reuse istniejącej nazwy -> zero ruchu w indeksie (dokument się nie zmienia)")
    void inFlightReuse_doesNotTouchIndex() {
        Long gameId = tx.execute(_ -> gameSubmissionService.createGame(
                gameRequest("Gra z istniejaca taksonomia", List.of("Rio Grande Games"),
                        List.of(new AuthorRequestDto("Uwe", "Rosenberg")), false),
                JANE).id());
        createdGameIds.add(gameId);

        verifyNoInteractions(taxonomySuggestService);
    }

    // ---------- kaskada moderacji ----------

    @Test
    @DisplayName("approve gry kaskadowo zatwierdza taksonomię -> upsert TYLKO przestawionych wpisów, ze APPROVED")
    void approveGame_reindexesCascadedTaxonomy() {
        Long gameId = tx.execute(_ -> gameSubmissionService.createGame(
                gameRequest("Gra do kaskady", List.of("Kaskadowy Wydawca"), List.of(), true), JANE).id());
        createdGameIds.add(gameId);
        clearInvocations(taxonomySuggestService);

        tx.executeWithoutResult(_ -> gameModerationService.approve(gameId, MODERATOR));

        ArgumentCaptor<List<TaxonomyDocument>> batch = documentBatchCaptor();
        verify(taxonomySuggestService).index(batch.capture());
        assertThat(batch.getValue()).singleElement().satisfies(document -> {
            assertThat(document.name()).isEqualTo("Kaskadowy Wydawca");
            assertThat(document.status()).isEqualTo(TaxonomyStatus.APPROVED);
        });
    }

    @Test
    @DisplayName("approve gry z samą zatwierdzoną taksonomią -> brak zdarzenia (nic nie przestawiono)")
    void approveGame_withNothingPending_doesNotTouchIndex() {
        Long gameId = tx.execute(_ -> gameSubmissionService.createGame(
                gameRequest("Gra bez oczekujacej taksonomii", List.of("Rio Grande Games"), List.of(), true),
                JANE).id());
        createdGameIds.add(gameId);
        clearInvocations(taxonomySuggestService);

        tx.executeWithoutResult(_ -> gameModerationService.approve(gameId, MODERATOR));

        verifyNoInteractions(taxonomySuggestService);
    }

    // ---------- kontrakt zdarzeń ----------

    @Test
    @DisplayName("rollback tworzenia wydawcy -> zero indeksowania (AFTER_COMMIT nie odpala)")
    void createPublisher_rolledBack_doesNotTouchIndex() {
        assertThatThrownBy(() -> tx.executeWithoutResult(_ -> {
            taxonomyService.createPublisher("Indeksowany Wydawca");
            throw new IllegalStateException("forced rollback after create");
        })).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(taxonomySuggestService);
    }

    @Test
    @DisplayName("awaria indeksu nie cofa akcji biznesowej — zdarzenie poszło, a wydawca został w bazie")
    void indexFailure_doesNotRollBackBusinessAction() {
        doThrow(new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE))
                .when(taxonomySuggestService).index(any());

        Long id = tx.execute(_ -> taxonomyService.createPublisher("Wydawca Mimo Awarii").getId());

        // bez tego verify test przechodziłby także wtedy, gdyby publikacja wyparowała z createPublisher:
        // stub nigdy by nie rzucił, a asercja poniżej mówiłaby tylko "create działa"
        verify(taxonomySuggestService).index(any());
        assertThat(publisherRepository.findById(id)).isPresent();
    }
}
