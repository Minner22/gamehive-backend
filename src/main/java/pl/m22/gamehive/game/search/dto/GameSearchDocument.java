package pl.m22.gamehive.game.search.dto;

import pl.m22.gamehive.game.model.ContentModerationTargetType;

import java.util.List;

public record GameSearchDocument(
        String id,
        ContentModerationTargetType targetType,
        Long targetId,
        String title,
        String description,
        String baseGameTitle,
        List<Long> publisherIds,
        List<Long> categoryIds,
        List<Long> mechanicIds,
        List<Long> authorIds,
        int minPlayers,
        int maxPlayers,
        int playingTimeMinutes,
        Integer yearPublished,
        int minAge,
        Long baseGameId) {
}
