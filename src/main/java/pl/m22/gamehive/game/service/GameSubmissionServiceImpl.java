package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.logging.CorrelationIdFilter;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.config.ModerationProperties;
import pl.m22.gamehive.game.dto.AuthorRequestDto;
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.dto.GameRequestDto;
import pl.m22.gamehive.game.event.ContentModerationAuditEvent;
import pl.m22.gamehive.game.mapper.GameMapper;
import pl.m22.gamehive.game.model.*;
import pl.m22.gamehive.game.repository.*;
import pl.m22.gamehive.user.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameSubmissionServiceImpl implements GameSubmissionService {

    private static final Set<ModerationStatus> MY_SUBMISSION_STATUSES = Set.of(ModerationStatus.DRAFT, ModerationStatus.PENDING, ModerationStatus.REJECTED);
    private static final Set<ModerationStatus> EDITABLE_STATUSES = Set.of(ModerationStatus.DRAFT, ModerationStatus.REJECTED);

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final GameRepository gameRepository;
    private final MechanicRepository mechanicRepository;
    private final PublisherRepository publisherRepository;
    private final GameMapper gameMapper;
    private final ModerationProperties moderationProperties;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public GameDto createGame(GameRequestDto request, Email submitterEmail) {

        validateDomainRules(request);

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

        attachAssociations(game, request);
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

        validateDomainRules(request);

        game.updateDetails(request.title(),
                request.description(),
                request.minPlayers(),
                request.maxPlayers(),
                request.playingTimeMinutes(),
                request.yearPublished(),
                request.minAge(),
                request.coverImageUrl());

        game.clearAssociations();
        attachAssociations(game, request);

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
    public GameDto findMySubmission(Long gameId, Email submitterEmail) {

        return gameMapper.toDto(findOwnGame(submitterEmail, gameId));
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

    private void validateDomainRules(GameRequestDto request) {

        if (request.minPlayers() > request.maxPlayers()) {
            throw new DomainException(ErrorCode.INVALID_PLAYER_COUNT);
        }

        if (isEmpty(request.publisherIds()) && isEmpty(request.newPublisherNames())) {
            throw new DomainException(ErrorCode.PUBLISHER_REQUIRED);
        }

        if (isEmpty(request.categoryIds())) {
            throw new DomainException(ErrorCode.CATEGORY_REQUIRED);
        }
    }

    private void attachAssociations(Game game, GameRequestDto request) {

        resolvePublishers(request).forEach(game::addPublisher);
        resolveCategories(request.categoryIds()).forEach(game::addCategory);
        resolveMechanics(request.mechanicIds()).forEach(game::addMechanic);
        resolveAuthors(request).forEach(game::addAuthor);
    }

    private List<Publisher> resolvePublishers(GameRequestDto request) {

        List<Publisher> publishers = new ArrayList<>(
                findAllOrThrow(publisherRepository, request.publisherIds(), ErrorCode.PUBLISHER_NOT_FOUND));

        for (String rawName : nullSafe(request.newPublisherNames())) {
            String name = rawName.trim();
            publishers.add(publisherRepository.findByName(name)
                    .orElseGet(() -> publisherRepository.save(Publisher.of(name, TaxonomyStatus.PENDING))));
        }

        return publishers;
    }

    private List<Category> resolveCategories(List<Long> categoryIds) {

        return findAllOrThrow(categoryRepository, categoryIds, ErrorCode.CATEGORY_NOT_FOUND);
    }

    private List<Mechanic> resolveMechanics(List<Long> mechanicIds) {

        return findAllOrThrow(mechanicRepository, mechanicIds, ErrorCode.MECHANIC_NOT_FOUND);
    }

    private List<Author> resolveAuthors(GameRequestDto request) {

        List<Author> authors = new ArrayList<>(
                findAllOrThrow(authorRepository, request.authorIds(), ErrorCode.AUTHOR_NOT_FOUND));

        for (AuthorRequestDto newAuthor : nullSafe(request.newAuthors())) {
            String firstName = newAuthor.firstName().trim();
            String lastName = newAuthor.lastName().trim();

            authors.add(authorRepository.findByFirstNameAndLastName(firstName, lastName)
                    .orElseGet(() -> authorRepository.save(Author.of(firstName, lastName, TaxonomyStatus.PENDING))));
        }

        return authors;
    }

    // findAllById deduplikuje, stąd porównanie z liczbą UNIKALNYCH id wykrywa brakujące wpisy
    private static <T> List<T> findAllOrThrow(JpaRepository<T, Long> repository, List<Long> ids, ErrorCode notFound) {

        List<Long> requested = nullSafe(ids);
        List<T> found = repository.findAllById(requested);

        if (found.size() != Set.copyOf(requested).size()) {
            throw new ApplicationException(notFound);
        }

        return found;
    }

    private static <T> List<T> nullSafe(List<T> list) {

        return list == null ? List.of() : list;
    }

    private static boolean isEmpty(List<?> list) {

        return list == null || list.isEmpty();
    }

    private void publishAudit(ContentModerationAction action, Long targetId, Email actor, String details) {

        eventPublisher.publishEvent(new ContentModerationAuditEvent(
                action, ContentModerationTargetType.GAME, targetId, actor.value(), details, currentCorrelationId()));
    }

    private String currentCorrelationId() {

        return MDC.get(CorrelationIdFilter.CORRELATION_ID);
    }
}
