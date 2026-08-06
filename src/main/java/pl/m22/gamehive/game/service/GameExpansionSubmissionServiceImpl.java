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
import pl.m22.gamehive.game.dto.GameExpansionDto;
import pl.m22.gamehive.game.dto.GameExpansionRequestDto;
import pl.m22.gamehive.game.mapper.GameExpansionMapper;
import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.user.service.UserService;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameExpansionSubmissionServiceImpl implements GameExpansionSubmissionService {

    private static final Set<ModerationStatus> MY_SUBMISSION_STATUSES =
            Set.of(ModerationStatus.DRAFT, ModerationStatus.PENDING, ModerationStatus.REJECTED);
    private static final Set<ModerationStatus> EDITABLE_STATUSES =
            Set.of(ModerationStatus.DRAFT, ModerationStatus.REJECTED);

    private final GameExpansionRepository expansionRepository;
    private final GameRepository gameRepository;
    private final GameExpansionMapper expansionMapper;
    private final GameExpansionContentWriter contentWriter;
    private final ModerationProperties moderationProperties;
    private final UserService userService;
    private final ContentModerationAuditPublisher auditPublisher;

    @Transactional
    @Override
    public GameExpansionDto createExpansion(GameExpansionRequestDto request, Email submitterEmail) {

        if (request.baseGameId() == null) {
            throw new DomainException(ErrorCode.BASE_GAME_REQUIRED);
        }

        Game baseGame = gameRepository.findById(request.baseGameId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.GAME_NOT_FOUND));

        contentWriter.validateDomainRules(request, baseGame);

        UUID submitterId = userService.findUserIdByEmail(submitterEmail);

        GameExpansion expansion = GameExpansion.builder()
                .baseGame(baseGame)
                .name(request.name())
                .description(request.description())
                .submittedBy(submitterId)
                .moderationStatus(request.submit() ? ModerationStatus.PENDING : ModerationStatus.DRAFT)
                .minPlayers(request.minPlayers())
                .maxPlayers(request.maxPlayers())
                .playingTimeMinutes(request.playingTimeMinutes())
                .minAge(request.minAge())
                .build();

        contentWriter.applyAssociations(expansion, request);
        expansionRepository.save(expansion);

        if (request.submit()) {
            publishAudit(ContentModerationAction.SUBMIT, expansion.getId(), submitterEmail, null);
        }

        return expansionMapper.toDto(expansion);
    }

    @Transactional
    @Override
    public GameExpansionDto updateExpansion(Long expansionId, GameExpansionRequestDto request, Email submitterEmail) {

        GameExpansion expansion = findOwnExpansion(submitterEmail, expansionId);

        if (!EDITABLE_STATUSES.contains(expansion.getModerationStatus())) {
            throw new DomainException(ErrorCode.EXPANSION_NOT_EDITABLE);
        }

        contentWriter.validateDomainRules(request, expansion.getBaseGame());

        expansion.updateDetails(request.name(),
                request.description(),
                request.minPlayers(),
                request.maxPlayers(),
                request.playingTimeMinutes(),
                request.minAge());

        expansion.clearAssociations();
        contentWriter.applyAssociations(expansion, request);

        publishAudit(ContentModerationAction.EDIT, expansion.getId(), submitterEmail, null);

        return expansionMapper.toDto(expansion);
    }

    @Transactional
    @Override
    public GameExpansionDto submitExpansion(Long expansionId, Email submitterEmail) {

        GameExpansion expansion = findOwnExpansion(submitterEmail, expansionId);

        contentWriter.validateBaseGameApproved(expansion.getBaseGame());

        switch (expansion.getModerationStatus()) {
            case DRAFT -> {
                expansion.submitForModeration();
                publishAudit(ContentModerationAction.SUBMIT, expansion.getId(), submitterEmail, null);
            }
            case REJECTED -> {
                if (expansion.getResubmissionCount() >= moderationProperties.getMaxResubmissions()) {
                    throw new DomainException(ErrorCode.RESUBMISSION_LIMIT_EXCEEDED);
                }
                expansion.resubmit();
                publishAudit(ContentModerationAction.RESUBMIT, expansion.getId(), submitterEmail, null);
            }
            default -> throw new DomainException(ErrorCode.EXPANSION_NOT_EDITABLE);
        }

        return expansionMapper.toDto(expansion);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<GameExpansionDto> findMySubmissions(Email submitterEmail, Pageable pageable) {

        UUID submitterId = userService.findUserIdByEmail(submitterEmail);

        return expansionRepository.findBySubmittedByAndModerationStatusIn(submitterId, MY_SUBMISSION_STATUSES, pageable)
                .map(expansionMapper::toDto);
    }

    private GameExpansion findOwnExpansion(Email submitterEmail, Long expansionId) {

        UUID submitterId = userService.findUserIdByEmail(submitterEmail);

        GameExpansion expansion = expansionRepository.findById(expansionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EXPANSION_NOT_FOUND));

        if (!expansion.getSubmittedBy().equals(submitterId)) {
            throw new ApplicationException(ErrorCode.EXPANSION_NOT_FOUND);
        }

        return expansion;
    }

private void publishAudit(ContentModerationAction action, Long targetId, Email actor, String details) {

        auditPublisher.publish(action, ContentModerationTargetType.EXPANSION, targetId, actor, details);
    }
}
