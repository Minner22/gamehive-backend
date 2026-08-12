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
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.dto.GameRequestDto;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.CategoryRepository;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;
import pl.m22.gamehive.game.search.dto.GameSearchDocument;
import pl.m22.gamehive.game.search.service.GameSearchService;
import pl.m22.gamehive.game.service.GameExpansionModerationService;
import pl.m22.gamehive.game.service.GameModerationService;
import pl.m22.gamehive.game.service.GameSubmissionService;
import pl.m22.gamehive.support.SeededUsers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Dowód wpięcia zdarzeń indeksujących. Klasa jest NIE-@Transactional, bo listener działa AFTER_COMMIT,
 * i dlatego — zgodnie z regułą z GH-118/#121 — tworzy oraz kasuje WŁASNE wiersze; mutowanie zasianych
 * gier rozlałoby się na całą instancję H2.
 */
@SpringBootTest
@ActiveProfiles("test")
class GameSearchIndexingTest {

    private static final Email MODERATOR = new Email("mark.moderator@example.com");
    private static final Email JANE = new Email("jane.smith@example.com");

    @Autowired GameModerationService gameModerationService;
    @Autowired GameExpansionModerationService expansionModerationService;
    @Autowired GameSubmissionService gameSubmissionService;
    @Autowired GameRepository gameRepository;
    @Autowired GameExpansionRepository expansionRepository;
    @Autowired PublisherRepository publisherRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PlatformTransactionManager txManager;

    // fallback podmieniony mockiem — sprawdzamy WPIĘCIE zdarzeń, nie samo Meili
    @MockitoBean GameSearchService gameSearchService;
    @MockitoBean JavaMailSender mailSender;

    private TransactionTemplate tx;
    private Long pendingGameId;

    // wiersze zakładane tylko przez część testów — kasowane w kolejności odwrotnej do tworzenia
    private final List<Long> createdExpansionIds = new ArrayList<>();
    private final List<Long> createdGameIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        pendingGameId = saveGame("Cel indeksu", ModerationStatus.PENDING, 30);
    }

    @AfterEach
    void cleanup() {
        createdExpansionIds.stream()
                .filter(expansionRepository::existsById)
                .forEach(expansionRepository::deleteById);
        createdGameIds.reversed().stream()
                .filter(gameRepository::existsById)
                .forEach(gameRepository::deleteById);
        createdExpansionIds.clear();
        createdGameIds.clear();
    }

    /** APPROVED nie jest stanem początkowym encji (builder dopuszcza tylko DRAFT/PENDING), więc idzie przez approve(). */
    private Long saveGame(String title, ModerationStatus status, int playingTimeMinutes) {
        Game game = Game.builder()
                .title(title).description("Gra na potrzeby testu indeksowania.")
                .submittedBy(SeededUsers.JANE_ID)
                .moderationStatus(status == ModerationStatus.DRAFT ? ModerationStatus.DRAFT : ModerationStatus.PENDING)
                .minPlayers(1).maxPlayers(4).playingTimeMinutes(playingTimeMinutes)
                .yearPublished(2020).minAge(8).coverImageUrl(null)
                .build();
        if (status == ModerationStatus.APPROVED) {
            game.approve(SeededUsers.MARK_ID);
        }
        game.addPublisher(publisherRepository.findById(1L).orElseThrow());
        game.addCategory(categoryRepository.findById(1L).orElseThrow());

        Long id = gameRepository.saveAndFlush(game).getId();
        createdGameIds.add(id);
        return id;
    }

    private Long saveExpansion(Long baseGameId, String name, ModerationStatus status) {
        GameExpansion expansion = GameExpansion.builder()
                .baseGame(gameRepository.findById(baseGameId).orElseThrow())
                .name(name).description("Dodatek bez nadpisań — wszystko dziedziczone.")
                .submittedBy(SeededUsers.JANE_ID).moderationStatus(ModerationStatus.PENDING)
                .build();
        if (status == ModerationStatus.APPROVED) {
            expansion.approve(SeededUsers.MARK_ID);
        }

        Long id = expansionRepository.saveAndFlush(expansion).getId();
        createdExpansionIds.add(id);
        return id;
    }

    private static GameRequestDto editRequest(int playingTimeMinutes) {
        return new GameRequestDto("Baza indeksu (edycja)", "Zmieniony opis.",
                1, 4, playingTimeMinutes, 2020, 8, null,
                List.of(1L), List.of(), List.of(1L), List.of(), List.of(), List.of(), false);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<GameSearchDocument>> documentBatchCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    // ---------- gry ----------

    @Test
    @DisplayName("approve gry po committcie -> upsert dokumentu 'game-{id}' z danymi gry")
    void approve_indexesGameDocument() {
        tx.executeWithoutResult(_ -> gameModerationService.approve(pendingGameId, MODERATOR));

        ArgumentCaptor<List<GameSearchDocument>> batch = documentBatchCaptor();
        verify(gameSearchService).index(batch.capture());
        assertThat(batch.getValue()).singleElement().satisfies(document -> {
            assertThat(document.id()).isEqualTo("game-" + pendingGameId);
            assertThat(document.title()).isEqualTo("Cel indeksu");
            assertThat(document.categoryIds()).containsExactly(1L);
        });
        verify(gameSearchService, never()).delete(any());
    }

    @Test
    @DisplayName("rollback approve -> zero indeksowania (AFTER_COMMIT nie odpala na wycofanej transakcji)")
    void approve_rolledBack_doesNotTouchIndex() {
        assertThatThrownBy(() -> tx.executeWithoutResult(_ -> {
            gameModerationService.approve(pendingGameId, MODERATOR);
            throw new IllegalStateException("forced rollback after approve");
        })).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(gameSearchService);
    }

    @Test
    @DisplayName("reject gry -> usunięcie dokumentu (niezmiennik: w indeksie tylko APPROVED)")
    void reject_removesGameDocument() {
        tx.executeWithoutResult(_ -> gameModerationService.reject(pendingGameId, "Za krótki opis", MODERATOR));

        verify(gameSearchService).delete("game-" + pendingGameId);
        verify(gameSearchService, never()).index(any());
    }

    @Test
    @DisplayName("hard-delete gry -> usunięcie dokumentu")
    void deleteGame_removesDocument() {
        tx.executeWithoutResult(_ -> gameModerationService.deleteGame(pendingGameId, MODERATOR));

        verify(gameSearchService).delete("game-" + pendingGameId);
    }

    @Test
    @DisplayName("unlock gry nie rusza indeksu (REJECTED -> DRAFT, dokumentu i tak nie było)")
    void unlock_doesNotTouchIndex() {
        tx.executeWithoutResult(_ -> gameModerationService.reject(pendingGameId, "Powód", MODERATOR));
        clearInvocations(gameSearchService);

        tx.executeWithoutResult(_ -> gameModerationService.unlock(pendingGameId, MODERATOR));

        verifyNoInteractions(gameSearchService);
    }

    @Test
    @DisplayName("edycja APPROVED gry -> JEDEN upsert z grą i jej APPROVED dodatkami (1 addDocuments, nie 1+N)")
    void updateApprovedGame_batchesGameAndInheritingExpansions() {
        Long baseGameId = saveGame("Baza indeksu", ModerationStatus.APPROVED, 45);
        Long firstExpansionId = saveExpansion(baseGameId, "Dodatek dziedziczący", ModerationStatus.APPROVED);
        Long secondExpansionId = saveExpansion(baseGameId, "Drugi dodatek", ModerationStatus.APPROVED);

        tx.executeWithoutResult(_ ->
                gameModerationService.updateApprovedGame(baseGameId, editRequest(90), MODERATOR));

        ArgumentCaptor<List<GameSearchDocument>> batch = documentBatchCaptor();
        verify(gameSearchService, times(1)).index(batch.capture());
        assertThat(batch.getValue()).extracting(GameSearchDocument::id)
                .containsExactlyInAnyOrder("game-" + baseGameId,
                        "expansion-" + firstExpansionId,
                        "expansion-" + secondExpansionId);
        assertThat(batch.getValue())
                .filteredOn(document -> document.targetId().equals(firstExpansionId))
                .singleElement()
                .satisfies(document -> {
                    assertThat(document.playingTimeMinutes()).isEqualTo(90);   // nowa wartość dziedziczona
                    assertThat(document.baseGameTitle()).isEqualTo("Baza indeksu (edycja)");
                });
    }

    @Test
    @DisplayName("edycja APPROVED gry bez dodatków -> tylko jeden upsert")
    void updateApprovedGame_withoutExpansions_indexesOnlyGame() {
        Long baseGameId = saveGame("Baza bez dodatków", ModerationStatus.APPROVED, 45);

        tx.executeWithoutResult(_ ->
                gameModerationService.updateApprovedGame(baseGameId, editRequest(60), MODERATOR));

        verify(gameSearchService, times(1)).index(any());
    }

    @Test
    @DisplayName("edycja gry bazowej NIE reindeksuje dodatku nie-APPROVED (nie ma go w indeksie)")
    void updateApprovedGame_skipsNonApprovedExpansions() {
        Long baseGameId = saveGame("Baza z draftem", ModerationStatus.APPROVED, 45);
        saveExpansion(baseGameId, "Dodatek oczekujący", ModerationStatus.PENDING);

        tx.executeWithoutResult(_ ->
                gameModerationService.updateApprovedGame(baseGameId, editRequest(75), MODERATOR));

        ArgumentCaptor<List<GameSearchDocument>> batch = documentBatchCaptor();
        verify(gameSearchService, times(1)).index(batch.capture());
        assertThat(batch.getValue()).singleElement()
                .extracting(GameSearchDocument::id).isEqualTo("game-" + baseGameId);
    }

    // ---------- dodatki ----------

    @Test
    @DisplayName("approve dodatku -> upsert 'expansion-{id}' z wartościami efektywnymi gry bazowej")
    void approveExpansion_indexesEffectiveValues() {
        Long baseGameId = saveGame("Baza dodatku", ModerationStatus.APPROVED, 45);
        Long expansionId = saveExpansion(baseGameId, "Dodatek do zatwierdzenia", ModerationStatus.PENDING);

        tx.executeWithoutResult(_ -> expansionModerationService.approve(expansionId, MODERATOR));

        ArgumentCaptor<List<GameSearchDocument>> batch = documentBatchCaptor();
        verify(gameSearchService).index(batch.capture());
        assertThat(batch.getValue()).singleElement().satisfies(document -> {
            assertThat(document.id()).isEqualTo("expansion-" + expansionId);
            assertThat(document.playingTimeMinutes()).isEqualTo(45);   // dziedziczone
            assertThat(document.baseGameTitle()).isEqualTo("Baza dodatku");
            assertThat(document.yearPublished()).isNull();
        });
    }

    @Test
    @DisplayName("hard-delete dodatku -> usunięcie dokumentu 'expansion-{id}'")
    void deleteExpansion_removesDocument() {
        Long baseGameId = saveGame("Baza do kasowania dodatku", ModerationStatus.APPROVED, 45);
        Long expansionId = saveExpansion(baseGameId, "Dodatek do usunięcia", ModerationStatus.APPROVED);

        tx.executeWithoutResult(_ -> expansionModerationService.deleteExpansion(expansionId, MODERATOR));

        verify(gameSearchService).delete("expansion-" + expansionId);
    }

    // ---------- ścieżki zgłoszeniowe ----------

    @Test
    @DisplayName("submit własnego DRAFT-u (DRAFT -> PENDING) nie rusza indeksu — tam nie ma nic APPROVED")
    void submitDraft_doesNotTouchIndex() {
        Long draftId = saveGame("Szkic do wysłania", ModerationStatus.DRAFT, 30);

        tx.executeWithoutResult(_ -> gameSubmissionService.submitGame(draftId, JANE));

        assertThat(gameRepository.findById(draftId).orElseThrow().getModerationStatus())
                .isEqualTo(ModerationStatus.PENDING);
        verifyNoInteractions(gameSearchService);
    }

    @Test
    @DisplayName("resubmit odrzuconego zgłoszenia (REJECTED -> PENDING) też nie rusza indeksu")
    void resubmitRejected_doesNotTouchIndex() {
        tx.executeWithoutResult(_ -> gameModerationService.reject(pendingGameId, "Za krótki opis", MODERATOR));
        clearInvocations(gameSearchService);

        tx.executeWithoutResult(_ -> gameSubmissionService.submitGame(pendingGameId, JANE));

        verifyNoInteractions(gameSearchService);
    }
}
