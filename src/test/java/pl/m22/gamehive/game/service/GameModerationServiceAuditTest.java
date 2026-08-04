package pl.m22.gamehive.game.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationAuditLog;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.repository.CategoryRepository;
import pl.m22.gamehive.game.repository.ContentModerationAuditLogRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;
import pl.m22.gamehive.support.SeededUsers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GameModerationServiceAuditTest {

    @Autowired GameModerationService moderationService;
    @Autowired GameRepository gameRepository;
    @Autowired PublisherRepository publisherRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ContentModerationAuditLogRepository auditRepository;
    @Autowired PlatformTransactionManager txManager;
    @MockitoBean JavaMailSender mailSender;

    private static final Email MODERATOR = new Email("mark.moderator@example.com");

    private Long gameId;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        Game game = Game.builder()
                .title("Cel audytu").description("Gra do decyzji moderacyjnej.")
                .submittedBy(SeededUsers.JANE_ID).moderationStatus(ModerationStatus.PENDING)
                .minPlayers(1).maxPlayers(4).playingTimeMinutes(30)
                .yearPublished(2020).minAge(8).coverImageUrl(null)
                .build();
        game.addPublisher(publisherRepository.findById(1L).orElseThrow());
        game.addCategory(categoryRepository.findById(1L).orElseThrow());
        gameId = gameRepository.saveAndFlush(game).getId();
    }

    @AfterEach
    void cleanup() {
        if (gameRepository.existsById(gameId)) {   // gra mogła zostać twardo usunięta w teście delete (GH-119)
            gameRepository.deleteById(gameId);
        }
        auditRepository.deleteAll();
    }

    private GameRequestDto editRequest() {
        return new GameRequestDto("Cel audytu (edycja)", "Zmieniony opis.",
                1, 4, 40, 2020, 8, null,
                List.of(1L), List.of(), List.of(1L), List.of(), List.of(), List.of(), false);
    }

    @Test
    @DisplayName("approve po committcie -> dokładnie jeden wpis APPROVE (bez FK do gry)")
    void approve_committed_writesSingleAuditEntry() {
        new TransactionTemplate(txManager).executeWithoutResult(_ ->
                moderationService.approve(gameId, MODERATOR));

        List<ContentModerationAuditLog> entries = auditRepository.findByTargetId(gameId);
        assertThat(entries).hasSize(1);
        ContentModerationAuditLog entry = entries.getFirst();
        assertThat(entry.getAction()).isEqualTo(ContentModerationAction.APPROVE);
        assertThat(entry.getTargetType()).isEqualTo(ContentModerationTargetType.GAME);
        assertThat(entry.getActor()).isEqualTo("mark.moderator@example.com");
    }

    @Test
    @DisplayName("reject po committcie -> jeden wpis REJECT, details = powód")
    void reject_committed_writesReasonInDetails() {
        new TransactionTemplate(txManager).executeWithoutResult(_ ->
                moderationService.reject(gameId, "Niepełny opis", MODERATOR));

        List<ContentModerationAuditLog> entries = auditRepository.findByTargetId(gameId);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getAction()).isEqualTo(ContentModerationAction.REJECT);
        assertThat(entries.getFirst().getDetails()).isEqualTo("Niepełny opis");
    }

    @Test
    @DisplayName("updateApprovedGame po committcie -> dokładnie jeden wpis EDIT, actor = moderator")
    void edit_committed_writesSingleEditEntry() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        // przenieś zasianą grę PENDING do APPROVED przez encję (bez zdarzenia audytu)
        tx.executeWithoutResult(_ -> gameRepository.findById(gameId).orElseThrow().approve(SeededUsers.MARK_ID));

        tx.executeWithoutResult(_ ->
                moderationService.updateApprovedGame(gameId, editRequest(), MODERATOR));

        List<ContentModerationAuditLog> entries = auditRepository.findByTargetId(gameId);
        assertThat(entries).extracting(ContentModerationAuditLog::getAction)
                .containsExactly(ContentModerationAction.EDIT);
        assertThat(entries.getFirst().getActor()).isEqualTo("mark.moderator@example.com");
    }

    @Test
    @DisplayName("deleteGame po committcie -> jeden wpis DELETE, details = tytuł, wpis przeżywa hard-delete gry")
    void delete_committed_writesDeleteEntry_survivingHardDelete() {
        new TransactionTemplate(txManager).executeWithoutResult(_ ->
                moderationService.deleteGame(gameId, MODERATOR));   // gra jest PENDING -> kwalifikuje się (wariant C)

        assertThat(gameRepository.findById(gameId)).isEmpty();      // twardy delete
        List<ContentModerationAuditLog> entries = auditRepository.findByTargetId(gameId);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getAction()).isEqualTo(ContentModerationAction.DELETE);
        assertThat(entries.getFirst().getDetails()).isEqualTo("Cel audytu");   // tytuł zasianej gry z setUp
    }

    @Test
    @DisplayName("rollback deleteGame -> brak wpisu audytu i gra nadal istnieje")
    void delete_rolledBack_noAuditEntry() {
        assertThatThrownBy(() ->
                new TransactionTemplate(txManager).executeWithoutResult(_ -> {
                    moderationService.deleteGame(gameId, MODERATOR);
                    throw new IllegalStateException("forced rollback after delete");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(auditRepository.findByTargetId(gameId)).isEmpty();
        assertThat(gameRepository.findById(gameId)).isPresent();
    }

    @Test
    @DisplayName("rollback approve -> brak wpisu audytu (AFTER_COMMIT nie odpala się na wycofanej transakcji)")
    void approve_rolledBack_noAuditEntry() {
        assertThatThrownBy(() ->
                new TransactionTemplate(txManager).executeWithoutResult(_ -> {
                    moderationService.approve(gameId, MODERATOR);
                    throw new IllegalStateException("forced rollback after approve");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(auditRepository.findByTargetId(gameId)).isEmpty();
    }
}
