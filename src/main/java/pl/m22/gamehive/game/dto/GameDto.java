package pl.m22.gamehive.game.dto;

import pl.m22.gamehive.common.persistence.ModerationStatus;

import java.util.List;

public record GameDto(
        Long id,
        String title,
        String description,
        int minPlayers,
        int maxPlayers,
        int playingTimeMinutes,
        int yearPublished,
        int minAge,
        String coverImageUrl,
        ModerationStatus moderationStatus,
        String rejectionReason,
        List<PublisherDto> publishers,
        List<CategoryDto> categories,
        List<MechanicDto> mechanics,
        List<AuthorDto> authors
) {
}

