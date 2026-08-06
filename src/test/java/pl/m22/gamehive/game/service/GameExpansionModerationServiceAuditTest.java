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
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.ContentModerationAuditLogRepository;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.support.SeededUsers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GameExpansionModerationServiceAuditTest {

    @Autowired GameExpansionModerationService moderationService;
    @Autowired GameExpansionRepository expansionRepository;
    @Autowired GameRepository gameRepository;
    @Autowired ContentModerationAuditLogRepository auditRepository;
    @Autowired PlatformTransactionManager txManager;
    @MockitoBean JavaMailSender mailSender;

    private static final Email MODERATOR = new Email("mark.moderator@example.com");

    private Long expansionId;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        GameExpansion expansion = GameExpansion.builder()
                .baseGame(gameRepository.findByTitle("Carcassonne").getFirst())
                .name("Cel audytu dodatku")
                .description("Dodatek do decyzji moderacyjnej.")
                .submittedBy(SeededUsers.JANE_ID)
                .moderationStatus(ModerationStatus.PENDING)
                .build();
        expansionId = expansionRepository.saveAndFlush(expansion).getId();
    }

    @AfterEach
    void cleanup() {
        if (expansionRepository.existsById(expansionId)) {
            expansionRepository.deleteById(expansionId);
        }
        auditRepository.deleteAll();
    }

    // gry i dodatki dzielą tabelę audytu przy niezależnych sekwencjach id — sam targetId nie identyfikuje wpisu
    private List<ContentModerationAuditLog> auditFor(Long id) {
        return auditRepository.findByTargetTypeAndTargetId(ContentModerationTargetType.EXPANSION, id);
    }

    @Test
    @DisplayName("approve po committcie -> dokładnie jeden wpis APPROVE z targetType=EXPANSION")
    void approve_committed_writesSingleAuditEntry() {
        new TransactionTemplate(txManager).executeWithoutResult(_ ->
                moderationService.approve(expansionId, MODERATOR));

        List<ContentModerationAuditLog> entries = auditFor(expansionId);
        assertThat(entries).hasSize(1);
        ContentModerationAuditLog entry = entries.getFirst();
        assertThat(entry.getAction()).isEqualTo(ContentModerationAction.APPROVE);
        assertThat(entry.getTargetType()).isEqualTo(ContentModerationTargetType.EXPANSION);
        assertThat(entry.getActor()).isEqualTo("mark.moderator@example.com");
    }

    @Test
    @DisplayName("reject po committcie -> jeden wpis REJECT, details = powód")
    void reject_committed_writesReasonInDetails() {
        new TransactionTemplate(txManager).executeWithoutResult(_ ->
                moderationService.reject(expansionId, "Niepełny opis", MODERATOR));

        List<ContentModerationAuditLog> entries = auditFor(expansionId);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getAction()).isEqualTo(ContentModerationAction.REJECT);
        assertThat(entries.getFirst().getDetails()).isEqualTo("Niepełny opis");
    }

    @Test
    @DisplayName("unlock po committcie -> jeden wpis UNLOCK")
    void unlock_committed_writesUnlockEntry() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(_ -> moderationService.reject(expansionId, "do poprawy", MODERATOR));
        auditRepository.deleteAll();

        tx.executeWithoutResult(_ -> moderationService.unlock(expansionId, MODERATOR));

        assertThat(auditFor(expansionId)).extracting(ContentModerationAuditLog::getAction)
                .containsExactly(ContentModerationAction.UNLOCK);
    }

    @Test
    @DisplayName("audyt dodatku nie miesza się z audytem gry o tym samym id")
    void audit_isScopedByTargetType() {
        new TransactionTemplate(txManager).executeWithoutResult(_ ->
                moderationService.approve(expansionId, MODERATOR));

        assertThat(auditRepository.findByTargetTypeAndTargetId(ContentModerationTargetType.GAME, expansionId))
                .isEmpty();
    }

    @Test
    @DisplayName("rollback approve -> brak wpisu audytu (AFTER_COMMIT nie odpala się na wycofanej transakcji)")
    void approve_rolledBack_noAuditEntry() {
        assertThatThrownBy(() ->
                new TransactionTemplate(txManager).executeWithoutResult(_ -> {
                    moderationService.approve(expansionId, MODERATOR);
                    throw new IllegalStateException("forced rollback after approve");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(auditFor(expansionId)).isEmpty();
    }
}
