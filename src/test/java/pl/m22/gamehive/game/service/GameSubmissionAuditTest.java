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
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.dto.GameRequestDto;
import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationAuditLog;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.repository.CategoryRepository;
import pl.m22.gamehive.game.repository.ContentModerationAuditLogRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;
import pl.m22.gamehive.support.SeededUsers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GameSubmissionAuditTest {

    @Autowired GameSubmissionService submissionService;
    @Autowired GameRepository gameRepository;
    @Autowired PublisherRepository publisherRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ContentModerationAuditLogRepository auditRepository;
    @Autowired PlatformTransactionManager txManager;
    @MockitoBean JavaMailSender mailSender;

    private static final Email JANE = new Email("jane.smith@example.com");

    // gry tworzone poza fixturami data.sql — sprzątane po każdym teście, by nie zanieczyszczać wspólnej bazy
    private final List<Long> createdGameIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        createdGameIds.forEach(gameRepository::deleteById);
        createdGameIds.clear();
        auditRepository.deleteAll();
    }

    // buduje i commituje grę o zadanym statusie (REJECTED = PENDING + reject()), z jednym wydawcą i kategorią
    private Long persistGame(ModerationStatus status) {
        ModerationStatus initial = status == ModerationStatus.REJECTED ? ModerationStatus.PENDING : status;
        Game game = Game.builder()
                .title("Audyt " + status).description("Gra pod audyt ścieżki zgłoszeń.")
                .submittedBy(SeededUsers.JANE_ID).moderationStatus(initial)
                .minPlayers(1).maxPlayers(4).playingTimeMinutes(30)
                .yearPublished(2020).minAge(8).coverImageUrl(null)
                .build();
        if (status == ModerationStatus.REJECTED) {
            game.reject("seed", SeededUsers.MARK_ID);
        }
        game.addPublisher(publisherRepository.findById(1L).orElseThrow());
        game.addCategory(categoryRepository.findById(1L).orElseThrow());
        Long id = gameRepository.saveAndFlush(game).getId();
        createdGameIds.add(id);
        return id;
    }

    private GameRequestDto request(boolean submit) {
        return new GameRequestDto("Nowe zgłoszenie audytu", "Opis zgłoszenia.",
                1, 4, 30, 2020, 8, null,
                List.of(1L), List.of(), List.of(1L), List.of(), List.of(), List.of(), submit);
    }

    // ---------- createGame ----------

    @Test
    @DisplayName("createGame submit=true -> wpis SUBMIT (gra wchodzi od razu do kolejki)")
    void createGame_submit_writesSubmitAudit() {
        GameDto created = submissionService.createGame(request(true), JANE);
        createdGameIds.add(created.id());

        assertThat(auditRepository.findByTargetId(created.id()))
                .extracting(ContentModerationAuditLog::getAction)
                .containsExactly(ContentModerationAction.SUBMIT);
    }

    @Test
    @DisplayName("createGame submit=false (DRAFT) -> BRAK wpisu audytu")
    void createGame_draft_noAudit() {
        GameDto created = submissionService.createGame(request(false), JANE);
        createdGameIds.add(created.id());

        assertThat(auditRepository.findByTargetId(created.id())).isEmpty();
    }

    // ---------- submitGame ----------

    @Test
    @DisplayName("submitGame(DRAFT) -> wpis SUBMIT, actor = właściciel")
    void submit_draft_writesSubmitAudit() {
        Long id = persistGame(ModerationStatus.DRAFT);

        submissionService.submitGame(id, JANE);

        List<ContentModerationAuditLog> entries = auditRepository.findByTargetId(id);
        assertThat(entries).extracting(ContentModerationAuditLog::getAction)
                .containsExactly(ContentModerationAction.SUBMIT);
        assertThat(entries.getFirst().getActor()).isEqualTo("jane.smith@example.com");
    }

    @Test
    @DisplayName("submitGame(REJECTED) -> wpis RESUBMIT")
    void submit_rejected_writesResubmitAudit() {
        Long id = persistGame(ModerationStatus.REJECTED);

        submissionService.submitGame(id, JANE);

        assertThat(auditRepository.findByTargetId(id))
                .extracting(ContentModerationAuditLog::getAction)
                .containsExactly(ContentModerationAction.RESUBMIT);
    }

    // ---------- updateGame ----------

    @Test
    @DisplayName("updateGame(DRAFT) -> wpis EDIT")
    void update_draft_writesEditAudit() {
        Long id = persistGame(ModerationStatus.DRAFT);

        submissionService.updateGame(id, request(false), JANE);

        assertThat(auditRepository.findByTargetId(id))
                .extracting(ContentModerationAuditLog::getAction)
                .containsExactly(ContentModerationAction.EDIT);
    }

    // ---------- rollback ----------

    @Test
    @DisplayName("rollback submitGame -> brak wpisu audytu (AFTER_COMMIT nie odpala się na wycofanej transakcji)")
    void submit_rolledBack_noAuditEntry() {
        Long id = persistGame(ModerationStatus.DRAFT);

        assertThatThrownBy(() ->
                new TransactionTemplate(txManager).executeWithoutResult(_ -> {
                    submissionService.submitGame(id, JANE);
                    throw new IllegalStateException("forced rollback after submit");
                })
        ).isInstanceOf(IllegalStateException.class);

        assertThat(auditRepository.findByTargetId(id)).isEmpty();
    }
}
