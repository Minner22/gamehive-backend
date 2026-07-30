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
        gameRepository.deleteById(gameId);
        auditRepository.deleteAll();
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
