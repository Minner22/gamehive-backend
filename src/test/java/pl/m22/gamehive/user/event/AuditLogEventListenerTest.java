package pl.m22.gamehive.user.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.service.AuditLogService;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogEventListenerTest {

    @Mock AuditLogService auditLogService;
    @InjectMocks AuditLogEventListener listener;

    @Test
    @DisplayName("onUserAudit() -> deleguje zdarzenie do AuditLogService.record()")
    void onUserAudit_delegatesToService() {

        UserAuditEvent event = new UserAuditEvent(
                AuditAction.FORCE_LOGOUT,
                UUID.fromString("0192a1b2-0000-7000-8000-000000000002"),
                "jane.smith@example.com",
                "john.doe@example.com",
                null,
                "corr-123");

        listener.onUserAudit(event);

        verify(auditLogService).record(event);
    }
}
