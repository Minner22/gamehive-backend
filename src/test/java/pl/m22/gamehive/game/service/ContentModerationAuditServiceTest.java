package pl.m22.gamehive.game.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.game.event.ContentModerationAuditEvent;
import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationAuditLog;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.repository.ContentModerationAuditLogRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ContentModerationAuditServiceTest {

    @Autowired ContentModerationAuditService auditService;
    @Autowired ContentModerationAuditLogRepository auditRepository;

    @AfterEach
    void cleanup() {

        auditRepository.deleteAll();
    }

    @Test
    @DisplayName("record() -> zapisuje wpis audytu moderacji z kompletem pól (REQUIRES_NEW commituje)")
    void record_persistsEntry() {

        ContentModerationAuditEvent event = new ContentModerationAuditEvent(
                ContentModerationAction.REJECT,
                ContentModerationTargetType.GAME,
                2L,
                "mark.moderator@example.com",
                "Duplikat istniejącej gry",
                "corr-abc");

        auditService.record(event);

        List<ContentModerationAuditLog> entries = auditRepository.findByTargetId(2L);
        assertThat(entries).hasSize(1);
        ContentModerationAuditLog saved = entries.getFirst();
        assertThat(saved.getAction()).isEqualTo(ContentModerationAction.REJECT);
        assertThat(saved.getTargetType()).isEqualTo(ContentModerationTargetType.GAME);
        assertThat(saved.getActor()).isEqualTo("mark.moderator@example.com");
        assertThat(saved.getDetails()).isEqualTo("Duplikat istniejącej gry");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-abc");
        assertThat(saved.getCreatedAt()).isNotNull();   // "kiedy" z @PrePersist (AbstractEntity)
    }
}
