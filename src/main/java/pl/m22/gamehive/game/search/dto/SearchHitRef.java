package pl.m22.gamehive.game.search.dto;

import pl.m22.gamehive.game.model.ContentModerationTargetType;

public record SearchHitRef(ContentModerationTargetType targetType, Long targetId) {
}
