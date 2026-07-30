package pl.m22.gamehive.game.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.game.service.ContentModerationAuditService;

/**
 * Utrwala wpis audytu moderacji w reakcji na ContentModerationAuditEvent. AFTER_COMMIT — wpis powstaje
 * tylko, gdy decyzja moderacyjna faktycznie się scommitowała (rollback => brak wpisu). Zapis idzie w osobnej
 * transakcji (ContentModerationAuditService.record jest @Transactional(REQUIRES_NEW)), bo pierwotna
 * transakcja jest już zamknięta.
 */
@Component
@RequiredArgsConstructor
public class ContentModerationAuditListener {

    private final ContentModerationAuditService contentModerationAuditService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onContentModerationAudit(ContentModerationAuditEvent event) {

        contentModerationAuditService.record(event);
    }
}
