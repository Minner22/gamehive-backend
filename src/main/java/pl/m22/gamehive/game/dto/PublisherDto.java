package pl.m22.gamehive.game.dto;

import pl.m22.gamehive.game.model.PublisherStatus;

public record PublisherDto(Long id, String name, PublisherStatus status) {
}
