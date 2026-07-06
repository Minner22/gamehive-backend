package pl.m22.gamehive.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.game.model.*;
import pl.m22.gamehive.game.repository.*;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaxonomyServiceImpl implements TaxonomyService {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final MechanicRepository mechanicRepository;
    private final PublisherRepository publisherRepository;
    private final GameRepository gameRepository;

    @Transactional(readOnly = true)
    @Override
    public List<Category> findAllCategories() {

        return categoryRepository.findAll();
    }

    @Transactional
    @Override
    public Category createCategory(String name) {

        if (categoryRepository.existsByName(name)) {
            throw new DomainException(ErrorCode.CATEGORY_NAME_EXISTS);
        }

        Category category = Category.of(name);
        categoryRepository.save(category);

        return category;
    }

    @Transactional
    @Override
    public Category renameCategory(Long id, String name) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!category.getName().equals(name) && categoryRepository.existsByName(name)) {
            throw new DomainException(ErrorCode.CATEGORY_NAME_EXISTS);
        }

        category.rename(name);

        return category;
    }

    @Transactional
    @Override
    public void deleteCategory(Long id) {

        if (!categoryRepository.existsById(id)) {
            throw new ApplicationException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        if (gameRepository.existsByCategoriesId(id)) {
            throw new DomainException(ErrorCode.CATEGORY_IN_USE);
        }

        categoryRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Mechanic> findAllMechanics() {

        return mechanicRepository.findAll();
    }

    @Transactional
    @Override
    public Mechanic createMechanic(String name) {

        if (mechanicRepository.existsByName(name)) {
            throw new DomainException(ErrorCode.MECHANIC_NAME_EXISTS);
        }

        Mechanic mechanic = Mechanic.of(name);
        mechanicRepository.save(mechanic);

        return mechanic;
    }

    @Transactional
    @Override
    public Mechanic renameMechanic(Long id, String name) {

        Mechanic mechanic = mechanicRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.MECHANIC_NOT_FOUND));

        if (!mechanic.getName().equals(name) && mechanicRepository.existsByName(name)) {
            throw new DomainException(ErrorCode.MECHANIC_NAME_EXISTS);
        }

        mechanic.rename(name);

        return mechanic;
    }

    @Transactional
    @Override
    public void deleteMechanic(Long id) {

        if (!mechanicRepository.existsById(id)) {
            throw new ApplicationException(ErrorCode.MECHANIC_NOT_FOUND);
        }

        if (gameRepository.existsByMechanicsId(id)) {
            throw new DomainException(ErrorCode.MECHANIC_IN_USE);
        }

        mechanicRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Publisher> findPublishers(PublisherStatus status) {

        return status == null
                ? publisherRepository.findAll()
                : publisherRepository.findByStatus(status);
    }

    @Transactional
    @Override
    public Publisher createPublisher(String name) {

        if (publisherRepository.existsByName(name)) {
            throw new DomainException(ErrorCode.PUBLISHER_NAME_EXISTS);
        }

        Publisher publisher = Publisher.of(name, PublisherStatus.APPROVED);
        publisherRepository.save(publisher);

        return publisher;
    }

    @Transactional
    @Override
    public Publisher approvePublisher(Long id) {

        Publisher publisher = publisherRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PUBLISHER_NOT_FOUND));

        if (publisher.getStatus() != PublisherStatus.APPROVED) {
            publisher.approve();
        }

        return publisher;
    }

    @Transactional
    @Override
    public void deletePublisher(Long id) {

        if (!publisherRepository.existsById(id)) {
            throw new ApplicationException(ErrorCode.PUBLISHER_NOT_FOUND);
        }

        if (gameRepository.existsByPublishersId(id)) {
            throw new DomainException(ErrorCode.PUBLISHER_IN_USE);
        }

        publisherRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Author> findAllAuthors() {

        return authorRepository.findAll();
    }

    @Transactional
    @Override
    public Author createAuthor(String firstName, String lastName) {

        if (authorRepository.existsByFirstNameAndLastName(firstName, lastName)) {
            throw new DomainException(ErrorCode.AUTHOR_NAME_EXISTS);
        }

        Author author = Author.of(firstName, lastName);
        authorRepository.save(author);

        return author;
    }

    @Transactional
    @Override
    public Author updateAuthor(Long id, String firstName, String lastName) {

        Author author = authorRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AUTHOR_NOT_FOUND));

        if (!(author.getFirstName().equals(firstName) && author.getLastName().equals(lastName))
                && authorRepository.existsByFirstNameAndLastName(firstName, lastName)) {
            throw new DomainException(ErrorCode.AUTHOR_NAME_EXISTS);
        }

        author.rename(firstName, lastName);

        return author;
    }

    @Transactional
    @Override
    public void deleteAuthor(Long id) {

        if (!authorRepository.existsById(id)) {
            throw new ApplicationException(ErrorCode.AUTHOR_NOT_FOUND);
        }

        if (gameRepository.existsByAuthorsId(id)) {
            throw new DomainException(ErrorCode.AUTHOR_IN_USE);
        }

        authorRepository.deleteById(id);
    }
}
