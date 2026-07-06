package pl.m22.gamehive.game.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.dto.GameRequestDto;

public interface GameSubmissionService {

    GameDto createGame(GameRequestDto request, Email submitterEmail);

    GameDto updateGame(Long gameId, GameRequestDto request, Email submitterEmail);

    GameDto submitGame(Long gameId, Email submitterEmail);

    GameDto findMySubmission(Long gameId, Email submitterEmail);

    Page<GameDto> findMySubmissions(Email submitterEmail, Pageable pageable);
}
