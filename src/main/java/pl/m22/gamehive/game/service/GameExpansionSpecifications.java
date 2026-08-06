package pl.m22.gamehive.game.service;

import org.springframework.data.jpa.domain.Specification;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.common.persistence.Specifications;
import pl.m22.gamehive.game.dto.GameExpansionLibraryFilter;
import pl.m22.gamehive.game.model.GameExpansion;

public final class GameExpansionSpecifications {

    private GameExpansionSpecifications() {
        throw new IllegalStateException("Utility class");
    }

    public static Specification<GameExpansion> library(GameExpansionLibraryFilter filter) {

        return Specification.allOf(
                statusApproved(),
                hasBaseGame(filter.baseGameId()),
                Specifications.joinEqualsIfPresent("categories", filter.categoryId()),
                Specifications.joinEqualsIfPresent("mechanics", filter.mechanicId())
        );
    }

    private static Specification<GameExpansion> statusApproved() {

        return (root, query, cb) -> cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED);
    }

    private static Specification<GameExpansion> hasBaseGame(Long baseGameId) {

        return (root, query, cb) -> baseGameId == null
                ? null
                : cb.equal(root.get("baseGame").get("id"), baseGameId);
    }
}
