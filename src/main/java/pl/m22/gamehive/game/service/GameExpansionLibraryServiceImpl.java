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
import pl.m22.gamehive.game.dto.GameExpansionDto;
import pl.m22.gamehive.game.dto.GameExpansionLibraryFilter;
import pl.m22.gamehive.game.mapper.GameExpansionMapper;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.user.service.UserService;

@Service
@RequiredArgsConstructor
public class GameExpansionLibraryServiceImpl implements GameExpansionLibraryService {

    private final GameExpansionRepository expansionRepository;
    private final GameExpansionMapper expansionMapper;
    private final UserService userService;

    @Transactional(readOnly = true)
    @Override
    public Page<GameExpansionDto> findLibrary(GameExpansionLibraryFilter filter, Pageable pageable) {

        return expansionRepository.findAll(GameExpansionSpecifications.library(filter), pageable)
                .map(expansionMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public GameExpansionDto findExpansion(Long expansionId, Email viewer) {

        GameExpansion expansion = expansionRepository.findById(expansionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EXPANSION_NOT_FOUND));

        if (expansion.getModerationStatus() == ModerationStatus.APPROVED) {
            return expansionMapper.toDto(expansion);
        }

        if (!expansion.getSubmittedBy().equals(userService.findUserIdByEmail(viewer))) {
            throw new ApplicationException(ErrorCode.EXPANSION_NOT_FOUND);
        }

        return expansionMapper.toDto(expansion);
    }
}
