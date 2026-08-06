package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.logging.CorrelationIdFilter;
import pl.m22.gamehive.game.event.ContentModerationAuditEvent;
import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationTargetType;

@Component
@RequiredArgsConstructor
public class ContentModerationAuditPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(ContentModerationAction action, ContentModerationTargetType targetType,
                        Long targetId, Email actor, String details) {

        eventPublisher.publishEvent(new ContentModerationAuditEvent(
                action, targetType, targetId, actor.value(), details, MDC.get(CorrelationIdFilter.CORRELATION_ID)));
    }
}
