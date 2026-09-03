package pl.m22.gamehive.game.model;

import pl.m22.gamehive.common.persistence.ModerationStatus;

public enum ModerationQueueStatus {

    PENDING(ModerationStatus.PENDING),
    REJECTED(ModerationStatus.REJECTED);

    private final ModerationStatus moderationStatus;

    ModerationQueueStatus(ModerationStatus moderationStatus) {

        this.moderationStatus = moderationStatus;
    }

    public ModerationStatus toModerationStatus() {

        return moderationStatus;
    }
}
