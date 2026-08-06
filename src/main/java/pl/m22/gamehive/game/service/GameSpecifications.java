package pl.m22.gamehive.game.service;

import org.springframework.data.jpa.domain.Specification;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.common.persistence.Specifications;
import pl.m22.gamehive.game.dto.GameLibraryFilter;
import pl.m22.gamehive.game.model.Game;

public final class GameSpecifications {

    private GameSpecifications() {
        throw new IllegalStateException("Utility class");
    }

    public static Specification<Game> library(GameLibraryFilter filter) {

        return Specification.allOf(
                statusApproved(),
                Specifications.joinEqualsIfPresent("publishers", filter.publisherId()),
                Specifications.joinEqualsIfPresent("categories", filter.categoryId()),
                Specifications.joinEqualsIfPresent("mechanics", filter.mechanicId()),
                supportsPlayers(filter.players()),
                Specifications.lessThanOrEqualToIfPresent("playingTimeMinutes", filter.maxPlayingTime()),
                Specifications.equalsIfPresent("yearPublished", filter.yearPublished()),
                Specifications.lessThanOrEqualToIfPresent("minAge", filter.age())
        );
    }

    private static Specification<Game> statusApproved() {

        return (root, query, cb) -> cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED);
    }

    // dwustronny zakres (min <= N <= max) — jedyny warunek nie sprowadzający się do prostego "atrybut op wartość"
    private static Specification<Game> supportsPlayers(Integer players) {

        return (root, query, cb) -> players == null ? null : cb.and(
                cb.lessThanOrEqualTo(root.<Integer>get("minPlayers"), players),
                cb.greaterThanOrEqualTo(root.<Integer>get("maxPlayers"), players));
    }
}
