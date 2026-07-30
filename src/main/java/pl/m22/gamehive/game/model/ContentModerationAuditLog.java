package pl.m22.gamehive.game.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.m22.gamehive.common.persistence.LongEntity;

@Entity
@Table(name = "content_moderation_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentModerationAuditLog extends LongEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ContentModerationAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentModerationTargetType targetType;

    // id gry — BEZ FK do games: wpis audytu musi przetrwać hard-delete gry (#119), jak user_audit_log bez FK do users
    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private String actor;

    // dla REJECT trzyma powód; dla pozostałych akcji zwykle null
    @Column(columnDefinition = "text")
    private String details;

    @Column(length = 64)
    private String correlationId;

    private ContentModerationAuditLog(ContentModerationAction action, ContentModerationTargetType targetType,
                                      Long targetId, String actor, String details, String correlationId) {

        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.actor = actor;
        this.details = details;
        this.correlationId = correlationId;
    }

    public static ContentModerationAuditLog of(ContentModerationAction action, ContentModerationTargetType targetType,
                                               Long targetId, String actor, String details, String correlationId) {

        return new ContentModerationAuditLog(action, targetType, targetId, actor, details, correlationId);
    }
}
