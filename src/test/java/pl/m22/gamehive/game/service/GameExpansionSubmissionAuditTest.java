package pl.m22.gamehive.game.service;

import org.junit.jupiter.api.AfterEach;
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
import pl.m22.gamehive.game.dto.GameExpansionDto;
import pl.m22.gamehive.game.dto.GameExpansionRequestDto;
import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationAuditLog;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.ContentModerationAuditLogRepository;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.support.SeededUsers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GameExpansionSubmissionAuditTest {

    @Autowired GameExpansionSubmissionService submissionService;
    @Autowired GameExpansionRepository expansionRepository;
    @Autowired GameRepository gameRepository;
    @Autowired ContentModerationAuditLogRepository auditRepository;
    @Autowired PlatformTransactionManager txManager;
    @MockitoBean JavaMailSender mailSender;

    private static final Email JANE = new Email("jane.smith@example.com");

    // dodatki tworzone poza fixturami data.sql — sprzątane po każdym teście, by nie zanieczyszczać wspólnej bazy
    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        createdIds.forEach(expansionRepository::deleteById);
        createdIds.clear();
        auditRepository.deleteAll();
    }

    // buduje i commituje dodatek o zadanym statusie (REJECTED = PENDING + reject())
    private Long persistExpansion(ModerationStatus status) {
        ModerationStatus initial = status == ModerationStatus.REJECTED ? ModerationStatus.PENDING : status;
        GameExpansion expansion = GameExpansion.builder()
                .baseGame(gameRepository.findByTitle("Carcassonne").getFirst())
                .name("Audyt dodatku " + status)
                .description("Dodatek pod audyt ścieżki zgłoszeń.")
                .submittedBy(SeededUsers.JANE_ID)
                .moderationStatus(initial)
                .build();
        if (status == ModerationStatus.REJECTED) {
            expansion.reject("seed", SeededUsers.MARK_ID);
        }
        Long id = expansionRepository.saveAndFlush(expansion).getId();
        createdIds.add(id);
        return id;
    }

    private GameExpansionRequestDto request(boolean submit) {
        Long baseGameId = gameRepository.findByTitle("Carcassonne").getFirst().getId();
        return new GameExpansionRequestDto(baseGameId, "Nowy dodatek audytu", "Opis dodatku.",
                null, null, null, null, List.of(1L), List.of(), submit);
    }

    // gry i dodatki dzielą tabelę audytu przy niezależnych sekwencjach id — sam targetId nie identyfikuje wpisu
    private List<ContentModerationAuditLog> auditFor(Long expansionId) {
        return auditRepository.findByTargetTypeAndTargetId(ContentModerationTargetType.EXPANSION, expansionId);
    }

    // ---------- createExpansion ----------

    @Test
    @DisplayName("createExpansion submit=true -> wpis SUBMIT z targetType=EXPANSION")
    void create_submit_writesSubmitAudit() {
        GameExpansionDto created = submissionService.createExpansion(request(true), JANE);
        createdIds.add(created.id());

        List<ContentModerationAuditLog> entries = auditFor(created.id());
        assertThat(entries).extracting(ContentModerationAuditLog::getAction)
                .containsExactly(ContentModerationAction.SUBMIT);
        assertThat(entries.getFirst().getTargetType()).isEqualTo(ContentModerationTargetType.EXPANSION);
        assertThat(entries.getFirst().getActor()).isEqualTo("jane.smith@example.com");
    }

    @Test
    @DisplayName("createExpansion submit=false (DRAFT) -> BRAK wpisu audytu")
    void create_draft_noAudit() {
        GameExpansionDto created = submissionService.createExpansion(request(false), JANE);
        createdIds.add(created.id());

        assertThat(auditFor(created.id())).isEmpty();
    }

    // ---------- submitExpansion ----------

    @Test
    @DisplayName("submitExpansion(DRAFT) -> wpis SUBMIT, actor = właściciel")
    void submit_draft_writesSubmitAudit() {
        Long id = persistExpansion(ModerationStatus.DRAFT);

        submissionService.submitExpansion(id, JANE);

        List<ContentModerationAuditLog> entries = auditFor(id);
        assertThat(entries).extracting(ContentModerationAuditLog::getAction)
                .containsExactly(ContentModerationAction.SUBMIT);
        assertThat(entries.getFirst().getActor()).isEqualTo("jane.smith@example.com");
    }

    @Test
    @DisplayName("submitExpansion(REJECTED) -> wpis RESUBMIT")
    void submit_rejected_writesResubmitAudit() {
        Long id = persistExpansion(ModerationStatus.REJECTED);

        submissionService.submitExpansion(id, JANE);

        assertThat(auditFor(id)).extracting(ContentModerationAuditLog::getAction)
                .containsExactly(ContentModerationAction.RESUBMIT);
    }

    // ---------- updateExpansion ----------

    @Test
    @DisplayName("updateExpansion(DRAFT) -> wpis EDIT")
    void update_draft_writesEditAudit() {
        Long id = persistExpansion(ModerationStatus.DRAFT);

        submissionService.updateExpansion(id, request(false), JANE);

        assertThat(auditFor(id)).extracting(ContentModerationAuditLog::getAction)
                .containsExactly(ContentModerationAction.EDIT);
    }

    // ---------- rollback ----------

    @Test
    @DisplayName("rollback submitExpansion -> brak wpisu audytu (AFTER_COMMIT nie odpala się na wycofanej transakcji)")
    void submit_rolledBack_noAuditEntry() {
        Long id = persistExpansion(ModerationStatus.DRAFT);

        assertThatThrownBy(() ->
                new TransactionTemplate(txManager).executeWithoutResult(_ -> {
                    submissionService.submitExpansion(id, JANE);
                    throw new IllegalStateException("forced rollback after submit");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(auditFor(id)).isEmpty();
    }
}
