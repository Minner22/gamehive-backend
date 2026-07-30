package pl.m22.gamehive.game.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.service.ContentModerationAuditService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContentModerationAuditListenerTest {

    @Mock ContentModerationAuditService auditService;
    @InjectMocks ContentModerationAuditListener listener;

    @Test
    @DisplayName("onContentModerationAudit() -> deleguje zdarzenie do ContentModerationAuditService.record()")
    void onContentModerationAudit_delegatesToService() {

        ContentModerationAuditEvent event = new ContentModerationAuditEvent(
                ContentModerationAction.APPROVE,
                ContentModerationTargetType.GAME,
                2L,
                "mark.moderator@example.com",
                null,
                "corr-123");

        listener.onContentModerationAudit(event);

        verify(auditService).record(event);
    }
}
