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
import pl.m22.gamehive.game.dto.AuthorRequestDto;
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.dto.GameRequestDto;
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

    @Transactional
    @Override
    public GameDto createGame(GameRequestDto request, Email submitterEmail) {

        validateDomainRules(request);

        UUID submitterId = userService.findUserByEmail(submitterEmail).getId();

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

        return gameMapper.toDto(game);
    }

    @Transactional
    @Override
    public GameDto updateGame(Long gameId, GameRequestDto request, Email submitterEmail) {

        Game game = findOwnGame(submitterEmail, gameId);

        if (!EDITABLE_STATUSES.contains(game.getModerationStatus())) {
            throw new DomainException(ErrorCode.GAME_NOT_EDITABLE);
        }

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

        return gameMapper.toDto(game);
    }

    @Transactional
    @Override
    public GameDto submitGame(Long gameId, Email submitterEmail) {

        Game game = findOwnGame(submitterEmail, gameId);

        switch (game.getModerationStatus()) {
            case DRAFT -> game.submitForModeration();
            case REJECTED -> {
                if (game.getResubmissionCount() >= moderationProperties.getMaxResubmissions()) {
                    throw new DomainException(ErrorCode.RESUBMISSION_LIMIT_EXCEEDED);
                }
                game.resubmit();
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

        UUID submitterId = userService.findUserByEmail(submitterEmail).getId();

        return gameRepository.findBySubmittedByAndModerationStatusIn(submitterId, MY_SUBMISSION_STATUSES, pageable)
                .map(gameMapper::toDto);
    }

    private Game findOwnGame(Email submitterEmail, Long gameId) {

        UUID submitterId = userService.findUserByEmail(submitterEmail).getId();

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
        resolveMecanics(request.mechanicIds()).forEach(game::addMechanic);
        resolveAuthors(request).forEach(game::addAuthor);
    }

    private List<Publisher> resolvePublishers(GameRequestDto request) {

        List<Publisher> publishers = new ArrayList<>();

        for (Long id : nullSafe(request.publisherIds())) {
            publishers.add(publisherRepository.findById(id)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.PUBLISHER_NOT_FOUND)));
        }

        for (String rawName : nullSafe(request.newPublisherNames())) {
            String name = rawName.trim();
            publishers.add(publisherRepository.findByName(name)
                    .orElseGet(() -> publisherRepository.save(Publisher.of(name, TaxonomyStatus.PENDING))));
        }

        return publishers;
    }

    private List<Category> resolveCategories(List<Long> categoryIds) {

        return nullSafe(categoryIds).stream()
                .map(id -> categoryRepository.findById(id)
                        .orElseThrow(() -> new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND)))
                .toList();
    }

    private List<Mechanic> resolveMecanics(List<Long> mechanicIds) {

        return nullSafe(mechanicIds).stream()
                .map(id -> mechanicRepository.findById(id)
                        .orElseThrow(() -> new ApplicationException(ErrorCode.MECHANIC_NOT_FOUND)))
                .toList();
    }

    private List<Author> resolveAuthors(GameRequestDto request) {

        List<Author> authors = new ArrayList<>();

        for (Long id : nullSafe(request.authorIds())) {
            authors.add(authorRepository.findById(id)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.AUTHOR_NOT_FOUND)));
        }

        for (AuthorRequestDto newAuthor : nullSafe(request.newAuthors())) {
            String firstName = newAuthor.firstName().trim();
            String lastName = newAuthor.lastName().trim();

            authors.add(authorRepository.findByFirstNameAndLastName(firstName, lastName)
                    .orElseGet(() -> authorRepository.save(Author.of(firstName, lastName, TaxonomyStatus.PENDING))));
        }

        return authors;
    }

    private static <T> List<T> nullSafe(List<T> list) {

        return list == null ? List.of() : list;
    }

    private static boolean isEmpty(List<?> list) {

        return list == null || list.isEmpty();
    }
}
