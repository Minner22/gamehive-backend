package pl.m22.gamehive.game.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.game.dto.GameExpansionDto;
import pl.m22.gamehive.game.dto.GameExpansionLibraryFilter;

public interface GameExpansionLibraryService {

    Page<GameExpansionDto> findLibrary(GameExpansionLibraryFilter filter, Pageable pageable);

    GameExpansionDto findExpansion(Long expansionId, Email viewer);
}
