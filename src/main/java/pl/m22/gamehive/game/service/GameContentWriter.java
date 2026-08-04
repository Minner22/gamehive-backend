package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.game.dto.AuthorRequestDto;
import pl.m22.gamehive.game.dto.GameRequestDto;
import pl.m22.gamehive.game.model.*;
import pl.m22.gamehive.game.repository.AuthorRepository;
import pl.m22.gamehive.game.repository.CategoryRepository;
import pl.m22.gamehive.game.repository.MechanicRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// wspólna logika zapisu treści gry: walidacja reguł domenowych + budowa relacji słownikowych
// (find-or-create nowych wydawców/autorów jako PENDING). Współdzielona przez zgłoszenia użytkownika
// (GH-117, GameSubmissionServiceImpl) i edycję biblioteki przez moderatora (GH-119, GameModerationServiceImpl)
@Component
@RequiredArgsConstructor
public class GameContentWriter {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final MechanicRepository mechanicRepository;
    private final PublisherRepository publisherRepository;

    // stała kolejność: INVALID_PLAYER_COUNT -> PUBLISHER_REQUIRED (id + nowe nazwy) -> CATEGORY_REQUIRED
    public void validateDomainRules(GameRequestDto request) {

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

    // dodaje do gry wszystkie cztery kolekcje; nowi wydawcy/autorzy tworzeni w locie ze statusem PENDING
    public void applyAssociations(Game game, GameRequestDto request) {

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
}
