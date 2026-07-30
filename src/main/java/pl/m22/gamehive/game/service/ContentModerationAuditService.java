package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.game.event.ContentModerationAuditEvent;
import pl.m22.gamehive.game.model.ContentModerationAuditLog;
import pl.m22.gamehive.game.repository.ContentModerationAuditLogRepository;

@Service
@RequiredArgsConstructor
public class ContentModerationAuditService {

    private final ContentModerationAuditLogRepository contentModerationAuditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ContentModerationAuditEvent event) {

        contentModerationAuditLogRepository.save(ContentModerationAuditLog.of(
                event.action(),
                event.targetType(),
                event.targetId(),
                event.actor(),
                event.details(),
                event.correlationId()
        ));
    }
}
