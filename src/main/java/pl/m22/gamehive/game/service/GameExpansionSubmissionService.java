package pl.m22.gamehive.game.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.game.dto.GameExpansionDto;
import pl.m22.gamehive.game.dto.GameExpansionRequestDto;

public interface GameExpansionSubmissionService {

    GameExpansionDto createExpansion(GameExpansionRequestDto request, Email submitterEmail);

    GameExpansionDto updateExpansion(Long expansionId, GameExpansionRequestDto request, Email submitterEmail);

    GameExpansionDto submitExpansion(Long expansionId, Email submitterEmail);

    Page<GameExpansionDto> findMySubmissions(Email submitterEmail, Pageable pageable);
}
