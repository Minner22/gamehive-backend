package pl.m22.gamehive.collection.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.collection.dto.ExpansionCollectionItemDto;
import pl.m22.gamehive.collection.dto.GameCollectionItemDto;
import pl.m22.gamehive.collection.mapper.CollectionMapper;
import pl.m22.gamehive.collection.model.ExpansionCollectionItem;
import pl.m22.gamehive.collection.model.GameCollectionItem;
import pl.m22.gamehive.collection.repository.ExpansionCollectionItemRepository;
import pl.m22.gamehive.collection.repository.GameCollectionItemRepository;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.user.service.UserService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionServiceImpl implements CollectionService {

    private final GameCollectionItemRepository gameCollectionRepository;
    private final ExpansionCollectionItemRepository expansionCollectionRepository;
    private final GameRepository gameRepository;
    private final GameExpansionRepository expansionRepository;
    private final CollectionMapper collectionMapper;
    private final UserService userService;

    @Transactional
    @Override
    public GameCollectionItemDto addGame(Long gameId, Email owner) {

        UUID userId = userService.findUserIdByEmail(owner);

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GAME_NOT_FOUND));

        if (game.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new DomainException(ErrorCode.GAME_NOT_APPROVED);
        }

        if (gameCollectionRepository.existsByUserIdAndGameId(userId, gameId)) {
            throw new DomainException(ErrorCode.ALREADY_IN_COLLECTION);
        }

        GameCollectionItem item = gameCollectionRepository.save(new GameCollectionItem(userId, game));

        return collectionMapper.toDto(item);
    }

    @Transactional
    @Override
    public void removeGame(Long gameId, Email owner) {

        UUID userId = userService.findUserIdByEmail(owner);

        GameCollectionItem item = gameCollectionRepository.findByUserIdAndGameId(userId, gameId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.COLLECTION_ITEM_NOT_FOUND));

        gameCollectionRepository.delete(item);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<GameCollectionItemDto> findMyGames(Email owner, Pageable pageable) {

        UUID userId = userService.findUserIdByEmail(owner);

        return gameCollectionRepository.findByUserId(userId, pageable)
                .map(collectionMapper::toDto);
    }

    @Transactional
    @Override
    public ExpansionCollectionItemDto addExpansion(Long expansionId, Email owner) {

        UUID userId = userService.findUserIdByEmail(owner);

        GameExpansion expansion = expansionRepository.findById(expansionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EXPANSION_NOT_FOUND));

        if (expansion.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new DomainException(ErrorCode.EXPANSION_NOT_APPROVED);
        }

        if (expansionCollectionRepository.existsByUserIdAndExpansionId(userId, expansionId)) {
            throw new DomainException(ErrorCode.ALREADY_IN_COLLECTION);
        }

        ExpansionCollectionItem item =
                expansionCollectionRepository.save(new ExpansionCollectionItem(userId, expansion));

        return collectionMapper.toDto(item);
    }

    @Transactional
    @Override
    public void removeExpansion(Long expansionId, Email owner) {

        UUID userId = userService.findUserIdByEmail(owner);

        ExpansionCollectionItem item = expansionCollectionRepository
                .findByUserIdAndExpansionId(userId, expansionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.COLLECTION_ITEM_NOT_FOUND));

        expansionCollectionRepository.delete(item);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ExpansionCollectionItemDto> findMyExpansions(Email owner, Pageable pageable) {

        UUID userId = userService.findUserIdByEmail(owner);

        return expansionCollectionRepository.findByUserId(userId, pageable)
                .map(collectionMapper::toDto);
    }
}
