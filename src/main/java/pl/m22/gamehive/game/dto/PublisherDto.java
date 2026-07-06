package pl.m22.gamehive.game.dto;

import pl.m22.gamehive.game.model.TaxonomyStatus;

public record PublisherDto(Long id, String name, TaxonomyStatus status) {
}
