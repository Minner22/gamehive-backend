package pl.m22.gamehive.game.search.dto;

import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.model.TaxonomyTargetType;

public record TaxonomyDocument(
        String id,
        TaxonomyTargetType targetType,
        Long targetId,
        String name,
        TaxonomyStatus status) {
}
