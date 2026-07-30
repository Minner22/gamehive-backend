package pl.m22.gamehive.game.event;

import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationTargetType;

public record ContentModerationAuditEvent(ContentModerationAction action,
                                          ContentModerationTargetType targetType,
                                          Long targetId,
                                          String actor,
                                          String details,
                                          String correlationId) {
}
