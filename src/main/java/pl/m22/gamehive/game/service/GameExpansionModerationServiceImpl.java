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
import pl.m22.gamehive.game.dto.GameExpansionModerationDto;
import pl.m22.gamehive.game.mapper.GameExpansionMapper;
import pl.m22.gamehive.game.model.ContentModerationAction;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.user.service.UserService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameExpansionModerationServiceImpl implements GameExpansionModerationService {

    private final GameExpansionRepository expansionRepository;
    private final GameExpansionMapper expansionMapper;
    private final GameExpansionContentWriter contentWriter;
    private final UserService userService;
    private final ContentModerationAuditPublisher auditPublisher;

    @Transactional(readOnly = true)
    @Override
    public Page<GameExpansionModerationDto> findPendingExpansions(Pageable pageable) {

        return expansionRepository.findByModerationStatus(ModerationStatus.PENDING, pageable)
                .map(expansionMapper::toModerationDto);
    }

    @Transactional
    @Override
    public GameExpansionModerationDto approve(Long expansionId, Email moderatorEmail) {

        GameExpansion expansion = findPendingExpansion(expansionId);

        contentWriter.validateBaseGameApproved(expansion.getBaseGame());

        UUID moderatorId = userService.findUserIdByEmail(moderatorEmail);
        expansion.approve(moderatorId);

        publishAudit(ContentModerationAction.APPROVE, expansionId, moderatorEmail, null);

        return expansionMapper.toModerationDto(expansion);
    }

    @Transactional
    @Override
    public GameExpansionModerationDto reject(Long expansionId, String reason, Email moderatorEmail) {

        if (reason == null || reason.isBlank()) {
            throw new DomainException(ErrorCode.REJECTION_REASON_REQUIRED);
        }

        GameExpansion expansion = findPendingExpansion(expansionId);
        UUID moderatorId = userService.findUserIdByEmail(moderatorEmail);

        String trimmedReason = reason.trim();
        expansion.reject(trimmedReason, moderatorId);

        publishAudit(ContentModerationAction.REJECT, expansionId, moderatorEmail, trimmedReason);

        return expansionMapper.toModerationDto(expansion);
    }

    @Transactional
    @Override
    public GameExpansionModerationDto unlock(Long expansionId, Email moderatorEmail) {

        GameExpansion expansion = expansionRepository.findById(expansionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EXPANSION_NOT_FOUND));

        if (expansion.getModerationStatus() != ModerationStatus.REJECTED) {
            throw new DomainException(ErrorCode.EXPANSION_NOT_REJECTED);
        }

        expansion.unlockForResubmission();

        publishAudit(ContentModerationAction.UNLOCK, expansionId, moderatorEmail, null);

        return expansionMapper.toModerationDto(expansion);
    }

    private GameExpansion findPendingExpansion(Long expansionId) {

        GameExpansion expansion = expansionRepository.findById(expansionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EXPANSION_NOT_FOUND));

        if (expansion.getModerationStatus() != ModerationStatus.PENDING) {
            throw new DomainException(ErrorCode.EXPANSION_NOT_PENDING);
        }

        return expansion;
    }

    private void publishAudit(ContentModerationAction action, Long targetId, Email actor, String details) {

        auditPublisher.publish(action, ContentModerationTargetType.EXPANSION, targetId, actor, details);
    }
}
