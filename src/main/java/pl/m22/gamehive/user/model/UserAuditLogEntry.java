package pl.m22.gamehive.user.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.m22.gamehive.common.persistence.LongEntity;

import java.util.UUID;

@Entity
@Table(name = "user_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAuditLogEntry extends LongEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(nullable = false)
    private UUID targetId;

    @Column(nullable = false)
    private String targetEmail;

    @Column(nullable = false)
    private String actor;

    @Column(columnDefinition = "text")
    private String details;

    @Column(length = 64)
    private String correlationId;

    private UserAuditLogEntry(AuditAction action, UUID targetId, String targetEmail, String actor, String details, String correlationId) {

        this.action = action;
        this.targetId = targetId;
        this.targetEmail = targetEmail;
        this.actor = actor;
        this.details = details;
        this.correlationId = correlationId;
    }

    public static UserAuditLogEntry of(AuditAction action, UUID targetId, String targetEmail, String actor, String details, String correlationId) {

        return new UserAuditLogEntry(action, targetId, targetEmail, actor, details, correlationId);
    }

}

