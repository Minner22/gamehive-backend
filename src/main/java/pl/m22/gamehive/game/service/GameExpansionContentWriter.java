package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.dto.GameExpansionRequestDto;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;

@Component
@RequiredArgsConstructor
public class GameExpansionContentWriter {

    private final TaxonomyResolver taxonomyResolver;

    public void validateBaseGameApproved(Game baseGame) {

        if (baseGame.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new DomainException(ErrorCode.BASE_GAME_NOT_APPROVED);
        }
    }

    public void validateDomainRules(GameExpansionRequestDto request, Game baseGame) {

        validateBaseGameApproved(baseGame);

        int effectiveMin = request.minPlayers() != null ? request.minPlayers() : baseGame.getMinPlayers();
        int effectiveMax = request.maxPlayers() != null ? request.maxPlayers() : baseGame.getMaxPlayers();

        if (effectiveMin > effectiveMax) {
            throw new DomainException(ErrorCode.INVALID_PLAYER_COUNT);
        }
    }

    public void applyAssociations(GameExpansion expansion, GameExpansionRequestDto request) {

        taxonomyResolver.resolveCategories(request.categoryIds()).forEach(expansion::addCategory);
        taxonomyResolver.resolveMechanics(request.mechanicIds()).forEach(expansion::addMechanic);
    }
}
