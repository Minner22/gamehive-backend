package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.dto.GameModerationDto;
import pl.m22.gamehive.game.dto.GameRequestDto;
import pl.m22.gamehive.game.mapper.GameMapper;
import pl.m22.gamehive.game.model.*;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.game.search.service.GameSearchIndexPublisher;
import pl.m22.gamehive.game.search.service.TaxonomyIndexPublisher;
import pl.m22.gamehive.user.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameModerationServiceImpl implements GameModerationService {

    private final GameRepository gameRepository;
    private final GameExpansionRepository gameExpansionRepository;
    private final GameMapper gameMapper;
    private final GameContentWriter contentWriter;
    private final UserService userService;
    private final ContentModerationAuditPublisher auditPublisher;
    private final GameSearchIndexPublisher searchIndexPublisher;
    private final TaxonomyIndexPublisher taxonomyIndexPublisher;

    @Transactional(readOnly = true)
    @Override
    public Page<GameModerationDto> findPendingGames(Pageable pageable) {

        return gameRepository.findByModerationStatus(ModerationStatus.PENDING, pageable)
                .map(gameMapper::toModerationDto);
    }

    @Transactional
    @Override
    public GameModerationDto approve(Long gameId, Email moderatorEmail) {

        Game game = findPendingGame(gameId);
        UUID moderatorId = userService.findUserIdByEmail(moderatorEmail);

        game.approve(moderatorId);
        approvePendingTaxonomy(game);

        publishAudit(ContentModerationAction.APPROVE, gameId, moderatorEmail, null);
        searchIndexPublisher.publishUpsert(game);

        return gameMapper.toModerationDto(game);
    }

    @Transactional
    @Override
    public GameModerationDto reject(Long gameId, String reason, Email moderatorEmail) {

        if (reason == null || reason.isBlank()) {
            throw new DomainException(ErrorCode.REJECTION_REASON_REQUIRED);
        }

        Game game = findPendingGame(gameId);
        UUID moderatorId = userService.findUserIdByEmail(moderatorEmail);

        String trimmedReason = reason.trim();
        game.reject(trimmedReason, moderatorId);

        publishAudit(ContentModerationAction.REJECT, gameId, moderatorEmail, trimmedReason);
        searchIndexPublisher.publishRemoval(ContentModerationTargetType.GAME, gameId);

        return gameMapper.toModerationDto(game);
    }

    @Transactional
    @Override
    public GameModerationDto unlock(Long gameId, Email moderatorEmail) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GAME_NOT_FOUND));

        if (game.getModerationStatus() != ModerationStatus.REJECTED) {
            throw new DomainException(ErrorCode.GAME_NOT_REJECTED);
        }

        game.unlockForResubmission();

        publishAudit(ContentModerationAction.UNLOCK, gameId, moderatorEmail, null);

        return gameMapper.toModerationDto(game);
    }

    @Transactional
    @Override
    public GameModerationDto updateApprovedGame(Long gameId, GameRequestDto request, Email moderatorEmail) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GAME_NOT_FOUND));

        if (game.getModerationStatus() != ModerationStatus.APPROVED) {
            throw new DomainException(ErrorCode.GAME_NOT_APPROVED);
        }

        contentWriter.validateDomainRules(request);

        game.updateDetails(request.title(),
                request.description(),
                request.minPlayers(),
                request.maxPlayers(),
                request.playingTimeMinutes(),
                request.yearPublished(),
                request.minAge(),
                request.coverImageUrl());

        game.clearAssociations();
        contentWriter.applyAssociations(game, request);
        approvePendingTaxonomy(game);

        publishAudit(ContentModerationAction.EDIT, gameId, moderatorEmail, null);
        searchIndexPublisher.publishUpsert(game,
                gameExpansionRepository.findByBaseGameIdAndModerationStatus(gameId, ModerationStatus.APPROVED));

        return gameMapper.toModerationDto(game);
    }

    @Transactional
    @Override
    public void deleteGame(Long gameId, Email moderatorEmail) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GAME_NOT_FOUND));

        if (game.getModerationStatus() == ModerationStatus.DRAFT) {
            throw new ApplicationException(ErrorCode.GAME_NOT_FOUND);
        }

        if (gameExpansionRepository.existsByBaseGameId(gameId)) {
            throw new DomainException(ErrorCode.GAME_HAS_EXPANSIONS);
        }

        String deletedTitle = game.getTitle();
        gameRepository.delete(game);

        publishAudit(ContentModerationAction.DELETE, gameId, moderatorEmail, deletedTitle);
        searchIndexPublisher.publishRemoval(ContentModerationTargetType.GAME, gameId);
    }

    private Game findPendingGame(Long gameId) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GAME_NOT_FOUND));

        if (game.getModerationStatus() != ModerationStatus.PENDING) {
            throw new DomainException(ErrorCode.GAME_NOT_PENDING);
        }

        return game;
    }

    private void approvePendingTaxonomy(Game game) {

        List<Publisher> approvedPublishers = new ArrayList<>();
        List<Author> approvedAuthors = new ArrayList<>();

        for (Publisher publisher : game.getPublishers()) {
            if (publisher.getStatus() != TaxonomyStatus.APPROVED) {
                publisher.approve();
                approvedPublishers.add(publisher);
            }
        }

        for (Author author : game.getAuthors()) {
            if (author.getStatus() != TaxonomyStatus.APPROVED) {
                author.approve();
                approvedAuthors.add(author);
            }
        }

        taxonomyIndexPublisher.publishUpsert(approvedPublishers, approvedAuthors);
    }

    private void publishAudit(ContentModerationAction action, Long targetId, Email actor, String details) {

        auditPublisher.publish(action, ContentModerationTargetType.GAME, targetId, actor, details);
    }
}
