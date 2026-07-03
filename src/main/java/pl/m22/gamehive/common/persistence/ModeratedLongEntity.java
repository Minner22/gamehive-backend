package pl.m22.gamehive.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ModeratedLongEntity extends LongEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationStatus moderationStatus;

    @Column(nullable = false, updatable = false)
    private UUID submittedBy;

    private UUID reviewedBy;

    private Instant reviewedAt;

    @Column(columnDefinition = "text")
    private String rejectionReason;

    @Column(nullable = false)
    private int resubmissionCount;

    protected ModeratedLongEntity(UUID submittedBy) {

        this.moderationStatus = ModerationStatus.PENDING;
        this.submittedBy = submittedBy;
        this.resubmissionCount = 0;
    }
}
