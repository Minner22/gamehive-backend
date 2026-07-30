package pl.m22.gamehive.game.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.game.dto.GameModerationDto;

public interface GameModerationService {

    Page<GameModerationDto> findPendingGames(Pageable pageable);

    GameModerationDto approve(Long gameId, Email moderatorEmail);

    GameModerationDto reject(Long gameId, String reason, Email moderatorEmail);

    GameModerationDto unlock(Long gameId, Email moderatorEmail);
}
