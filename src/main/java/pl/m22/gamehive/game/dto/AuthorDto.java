package pl.m22.gamehive.game.dto;

import pl.m22.gamehive.game.model.TaxonomyStatus;

public record AuthorDto(Long id, String firstName, String lastName, TaxonomyStatus status) {
}
