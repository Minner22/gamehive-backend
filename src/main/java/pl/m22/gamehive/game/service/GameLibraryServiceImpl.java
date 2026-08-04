package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.dto.GameLibraryFilter;
import pl.m22.gamehive.game.mapper.GameMapper;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.user.service.UserService;

@Service
@RequiredArgsConstructor
public class GameLibraryServiceImpl implements GameLibraryService {

    private final GameRepository gameRepository;
    private final GameMapper gameMapper;
    private final UserService userService;

    @Transactional(readOnly = true)
    @Override
    public Page<GameDto> findLibrary(GameLibraryFilter filter, Pageable pageable) {

        return gameRepository.findAll(GameSpecifications.library(filter), pageable)
                .map(gameMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public GameDto findGame(Long gameId, Email viewer) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GAME_NOT_FOUND));

        // bibliotekę (APPROVED) widzi każdy zalogowany — bez rozwiązywania właściciela (gorąca ścieżka odczytu)
        if (game.getModerationStatus() == ModerationStatus.APPROVED) {
            return gameMapper.toDto(game);
        }

        // własne zgłoszenie właściciel widzi w dowolnym statusie; cudze nie-APPROVED jest nieodróżnialne
        // od nieistniejącego (enumeration-safe, jak logowanie)
        if (!game.getSubmittedBy().equals(userService.findUserIdByEmail(viewer))) {
            throw new ApplicationException(ErrorCode.GAME_NOT_FOUND);
        }

        return gameMapper.toDto(game);
    }
}
