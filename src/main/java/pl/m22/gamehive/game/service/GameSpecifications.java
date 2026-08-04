package pl.m22.gamehive.game.service;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.dto.GameLibraryFilter;
import pl.m22.gamehive.game.model.Game;

public final class GameSpecifications {

    private GameSpecifications() {
        throw new IllegalStateException("Utility class");
    }

    public static Specification<Game> library(GameLibraryFilter filter) {

        return Specification.allOf(
                statusApproved(),
                hasPublisher(filter.publisherId()),
                hasCategory(filter.categoryId()),
                hasMechanic(filter.mechanicId()),
                supportsPlayers(filter.players()),
                playingTimeAtMost(filter.maxPlayingTime()),
                yearEquals(filter.yearPublished()),
                minAgeAtMost(filter.age())
        );
    }

    private static Specification<Game> statusApproved() {

        return (root, query, cb) -> cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED);
    }

    private static Specification<Game> hasPublisher(Long id) {

        return joinEquals("publishers", id);
    }

    private static Specification<Game> hasCategory(Long id) {

        return joinEquals("categories", id);
    }

    private static Specification<Game> hasMechanic(Long id) {

        return joinEquals("mechanics", id);
    }

    private static Specification<Game> joinEquals(String association, Long id) {

        return (root, query, cb) -> {
            if (id == null) {
                return null;
            }
            if (query != null) {
                query.distinct(true);
            }
            return cb.equal(root.join(association, JoinType.INNER).get("id"), id);
        };
    }

    private static Specification<Game> supportsPlayers(Integer players) {

        return (root, query, cb) -> players == null ? null : cb.and(
                cb.lessThanOrEqualTo(root.<Integer>get("minPlayers"), players),
                cb.greaterThanOrEqualTo(root.<Integer>get("maxPlayers"), players));
    }

    private static Specification<Game> playingTimeAtMost(Integer max) {

        return (root, query, cb) -> max == null
                ? null
                : cb.lessThanOrEqualTo(root.<Integer>get("playingTimeMinutes"), max);
    }

    private static Specification<Game> yearEquals(Integer year) {

        return (root, query, cb) -> year == null ? null : cb.equal(root.get("yearPublished"), year);
    }

    private static Specification<Game> minAgeAtMost(Integer age) {

        return (root, query, cb) -> age == null ? null : cb.lessThanOrEqualTo(root.<Integer>get("minAge"), age);
    }
}
