package pl.m22.gamehive.game.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.dto.GameLibraryFilter;

public interface GameLibraryService {

    Page<GameDto> findLibrary(GameLibraryFilter filter, Pageable pageable);

    GameDto findGame(Long gameId, Email viewer);
}
