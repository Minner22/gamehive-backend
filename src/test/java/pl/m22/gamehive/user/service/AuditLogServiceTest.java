package pl.m22.gamehive.user.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.event.UserAuditEvent;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.model.UserAuditLogEntry;
import pl.m22.gamehive.user.repository.UserAuditLogRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuditLogServiceTest {

    @Autowired AuditLogService auditLogService;
    @Autowired UserAuditLogRepository auditLogRepository;

    @AfterEach
    void cleanup() {

        auditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("recordAudit() -> zapisuje wpis audytu z kompletem pól (REQUIRES_NEW commituje)")
    void record_Audit_persistsEntry() {

        UserAuditEvent event = new UserAuditEvent(
                AuditAction.ROLE_CHANGE,
                SeededUsers.JANE_ID,
                "jane.smith@example.com",
                "john.doe@example.com",
                "{\"before\":[\"ROLE_USER\"],\"after\":[\"ROLE_MODERATOR\"]}",
                "corr-xyz");

        auditLogService.recordAudit(event);

        List<UserAuditLogEntry> entries = auditLogRepository.findByTargetId(SeededUsers.JANE_ID);
        assertThat(entries).hasSize(1);
        UserAuditLogEntry saved = entries.getFirst();
        assertThat(saved.getAction()).isEqualTo(AuditAction.ROLE_CHANGE);
        assertThat(saved.getTargetEmail()).isEqualTo("jane.smith@example.com");
        assertThat(saved.getActor()).isEqualTo("john.doe@example.com");
        assertThat(saved.getDetails()).contains("ROLE_MODERATOR");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-xyz");
        assertThat(saved.getCreatedAt()).isNotNull();   // "kiedy" z @PrePersist (AbstractEntity)
    }
}
