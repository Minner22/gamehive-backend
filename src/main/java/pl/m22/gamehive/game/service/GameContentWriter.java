package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.game.dto.AuthorRequestDto;
import pl.m22.gamehive.game.dto.GameRequestDto;
import pl.m22.gamehive.game.model.Author;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.Publisher;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.repository.AuthorRepository;
import pl.m22.gamehive.game.repository.PublisherRepository;
import pl.m22.gamehive.game.search.service.TaxonomyIndexPublisher;

import java.util.ArrayList;
import java.util.List;

import static pl.m22.gamehive.common.persistence.RepositoryLookups.findAllOrThrow;
import static pl.m22.gamehive.common.persistence.RepositoryLookups.nullSafe;

@Component
@RequiredArgsConstructor
public class GameContentWriter {

    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final TaxonomyResolver taxonomyResolver;
    private final TaxonomyIndexPublisher taxonomyIndexPublisher;

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

    public void applyAssociations(Game game, GameRequestDto request) {

        List<Publisher> createdPublishers = new ArrayList<>();
        List<Author> createdAuthors = new ArrayList<>();

        resolvePublishers(request, createdPublishers).forEach(game::addPublisher);
        taxonomyResolver.resolveCategories(request.categoryIds()).forEach(game::addCategory);
        taxonomyResolver.resolveMechanics(request.mechanicIds()).forEach(game::addMechanic);
        resolveAuthors(request, createdAuthors).forEach(game::addAuthor);

        taxonomyIndexPublisher.publishUpsert(createdPublishers, createdAuthors);
    }

    private List<Publisher> resolvePublishers(GameRequestDto request, List<Publisher> created) {

        List<Publisher> publishers = new ArrayList<>(
                findAllOrThrow(publisherRepository, request.publisherIds(), ErrorCode.PUBLISHER_NOT_FOUND));

        for (String rawName : nullSafe(request.newPublisherNames())) {
            String name = rawName.trim();
            publishers.add(publisherRepository.findByName(name)
                    .orElseGet(() -> {
                        Publisher publisher = publisherRepository.save(Publisher.of(name, TaxonomyStatus.PENDING));
                        created.add(publisher);
                        return publisher;
                    }));
        }

        return publishers;
    }

    private List<Author> resolveAuthors(GameRequestDto request, List<Author> created) {

        List<Author> authors = new ArrayList<>(
                findAllOrThrow(authorRepository, request.authorIds(), ErrorCode.AUTHOR_NOT_FOUND));

        for (AuthorRequestDto newAuthor : nullSafe(request.newAuthors())) {
            String firstName = newAuthor.firstName().trim();
            String lastName = newAuthor.lastName().trim();

            authors.add(authorRepository.findByFirstNameAndLastName(firstName, lastName)
                    .orElseGet(() -> {
                        Author author = authorRepository.save(Author.of(firstName, lastName, TaxonomyStatus.PENDING));
                        created.add(author);
                        return author;
                    }));
        }

        return authors;
    }

    private static boolean isEmpty(List<?> list) {

        return list == null || list.isEmpty();
    }
}
