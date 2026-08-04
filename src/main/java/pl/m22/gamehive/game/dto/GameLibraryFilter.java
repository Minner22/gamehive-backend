package pl.m22.gamehive.game.dto;

public record GameLibraryFilter(
        Long publisherId,
        Long categoryId,
        Long mechanicId,
        Integer players,
        Integer maxPlayingTime,
        Integer yearPublished,
        Integer age
) {
}
