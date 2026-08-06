package pl.m22.gamehive.game.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.game.dto.GameExpansionModerationDto;
import pl.m22.gamehive.game.dto.GameExpansionRequestDto;

public interface GameExpansionModerationService {

    Page<GameExpansionModerationDto> findPendingExpansions(Pageable pageable);

    GameExpansionModerationDto approve(Long expansionId, Email moderatorEmail);

    GameExpansionModerationDto reject(Long expansionId, String reason, Email moderatorEmail);

    GameExpansionModerationDto unlock(Long expansionId, Email moderatorEmail);

    GameExpansionModerationDto updateApprovedExpansion(Long expansionId, GameExpansionRequestDto request, Email moderatorEmail);

    void deleteExpansion(Long expansionId, Email moderatorEmail);
}
