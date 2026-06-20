package pl.m22.gamehive.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.user.event.UserAuditEvent;
import pl.m22.gamehive.user.model.UserAuditLogEntry;
import pl.m22.gamehive.user.repository.UserAuditLogRepository;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final UserAuditLogRepository userAuditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(UserAuditEvent event) {

        userAuditLogRepository.save(UserAuditLogEntry.of(
                event.action(),
                event.targetId(),
                event.targetEmail(),
                event.actor(),
                event.details(),
                event.correlationId()
        ));
    }
}
