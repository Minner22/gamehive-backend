package pl.m22.gamehive.game.search.dto;

import pl.m22.gamehive.game.model.ContentModerationTargetType;

public record GameSearchFilter(
        ContentModerationTargetType targetType,
        Long publisherId,
        Long categoryId,
        Long mechanicId,
        Long authorId,
        Long baseGameId,
        Integer players,
        Integer maxPlayingTime,
        Integer yearPublished,
        Integer age) {
}
