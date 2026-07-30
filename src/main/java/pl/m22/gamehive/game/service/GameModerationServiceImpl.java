package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.logging.CorrelationIdFilter;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.dto.GameModerationDto;
import pl.m22.gamehive.game.event.ContentModerationAuditEvent;
import pl.m22.gamehive.game.mapper.GameMapper;
import pl.m22.gamehive.game.model.*;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.user.service.UserService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameModerationServiceImpl implements GameModerationService {

    private final GameRepository gameRepository;
    private final GameMapper gameMapper;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

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

    private Game findPendingGame(Long gameId) {

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GAME_NOT_FOUND));

        if (game.getModerationStatus() != ModerationStatus.PENDING) {
            throw new DomainException(ErrorCode.GAME_NOT_PENDING);
        }

        return game;
    }

    // approve gry zatwierdza również jej wydawców i autorów PENDING (w tej samej transakcji); APPROVED bez zmian
    private void approvePendingTaxonomy(Game game) {

        for (Publisher publisher : game.getPublishers()) {
            if (publisher.getStatus() != TaxonomyStatus.APPROVED) {
                publisher.approve();
            }
        }

        for (Author author : game.getAuthors()) {
            if (author.getStatus() != TaxonomyStatus.APPROVED) {
                author.approve();
            }
        }
    }

    private void publishAudit(ContentModerationAction action, Long targetId, Email actor, String details) {

        eventPublisher.publishEvent(new ContentModerationAuditEvent(
                action, ContentModerationTargetType.GAME, targetId, actor.value(), details, currentCorrelationId()));
    }

    private String currentCorrelationId() {

        return MDC.get(CorrelationIdFilter.CORRELATION_ID);
    }
}
