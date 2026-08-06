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
import pl.m22.gamehive.game.config.ModerationProperties;
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.dto.GameRequestDto;
import pl.m22.gamehive.game.mapper.GameMapper;
import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.user.service.UserService;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameSubmissionServiceImpl implements GameSubmissionService {

    private static final Set<ModerationStatus> MY_SUBMISSION_STATUSES = Set.of(ModerationStatus.DRAFT, ModerationStatus.PENDING, ModerationStatus.REJECTED);
    private static final Set<ModerationStatus> EDITABLE_STATUSES = Set.of(ModerationStatus.DRAFT, ModerationStatus.REJECTED);

    private final GameRepository gameRepository;
    private final GameMapper gameMapper;
    private final GameContentWriter contentWriter;
    private final ModerationProperties moderationProperties;
    private final UserService userService;
    private final ContentModerationAuditPublisher auditPublisher;

    @Transactional
    @Override
    public GameDto createGame(GameRequestDto request, Email submitterEmail) {

        contentWriter.validateDomainRules(request);

        UUID submitterId = userService.findUserIdByEmail(submitterEmail);

        Game game = Game.builder()
                .title(request.title())
                .description(request.description())
                .submittedBy(submitterId)
                .moderationStatus(request.submit() ? ModerationStatus.PENDING : ModerationStatus.DRAFT)
                .minPlayers(request.minPlayers())
                .maxPlayers(request.maxPlayers())
                .playingTimeMinutes(request.playingTimeMinutes())
                .yearPublished(request.yearPublished())
                .minAge(request.minAge())
                .coverImageUrl(request.coverImageUrl())
                .build();

        contentWriter.applyAssociations(game, request);
        gameRepository.save(game);

        // audyt tylko dla realnego wejścia do kolejki (PENDING); szkic (DRAFT) nie jest zdarzeniem moderacji
        if (request.submit()) {
            publishAudit(ContentModerationAction.SUBMIT, game.getId(), submitterEmail, null);
        }

        return gameMapper.toDto(game);
    }

    @Transactional
    @Override
    public GameDto updateGame(Long gameId, GameRequestDto request, Email submitterEmail) {

        Game game = findOwnGame(submitterEmail, gameId);

        if (!EDITABLE_STATUSES.contains(game.getModerationStatus())) {
            throw new DomainException(ErrorCode.GAME_NOT_EDITABLE);
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

        publishAudit(ContentModerationAction.EDIT, game.getId(), submitterEmail, null);

        return gameMapper.toDto(game);
    }

    @Transactional
    @Override
    public GameDto submitGame(Long gameId, Email submitterEmail) {

        Game game = findOwnGame(submitterEmail, gameId);

        switch (game.getModerationStatus()) {
            case DRAFT -> {
                game.submitForModeration();
                publishAudit(ContentModerationAction.SUBMIT, game.getId(), submitterEmail, null);
            }
            case REJECTED -> {
                if (game.getResubmissionCount() >= moderationProperties.getMaxResubmissions()) {
                    throw new DomainException(ErrorCode.RESUBMISSION_LIMIT_EXCEEDED);
                }
                game.resubmit();
                publishAudit(ContentModerationAction.RESUBMIT, game.getId(), submitterEmail, null);
            }
            default -> throw new DomainException(ErrorCode.GAME_NOT_EDITABLE);
        }

        return gameMapper.toDto(game);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<GameDto> findMySubmissions(Email submitterEmail, Pageable pageable) {

        UUID submitterId = userService.findUserIdByEmail(submitterEmail);

        return gameRepository.findBySubmittedByAndModerationStatusIn(submitterId, MY_SUBMISSION_STATUSES, pageable)
                .map(gameMapper::toDto);
    }

    private Game findOwnGame(Email submitterEmail, Long gameId) {

        UUID submitterId = userService.findUserIdByEmail(submitterEmail);

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.GAME_NOT_FOUND));

        if (!game.getSubmittedBy().equals(submitterId)) {
            throw new ApplicationException(ErrorCode.GAME_NOT_FOUND);
        }

        return game;
    }

    private void publishAudit(ContentModerationAction action, Long targetId, Email actor, String details) {

        auditPublisher.publish(action, ContentModerationTargetType.GAME, targetId, actor, details);
    }
}
