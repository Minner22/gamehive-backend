package pl.m22.gamehive.user.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.user.service.AuditLogService;

/**
 * Utrwala wpis audytu w reakcji na UserAuditEvent. AFTER_COMMIT — wpis powstaje tylko, gdy akcja
 * biznesowa faktycznie się scommitowała (rollback => brak wpisu). Zapis idzie w osobnej transakcji
 * (AuditLogService.record jest @Transactional(REQUIRES_NEW)), bo pierwotna transakcja jest już zamknięta.
 */
@Component
@RequiredArgsConstructor
public class AuditLogEventListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserAudit(UserAuditEvent event) {

        auditLogService.recordAudit(event);
    }
}
