package pl.m22.gamehive.game.service;

import pl.m22.gamehive.game.model.*;

import java.util.List;

public interface TaxonomyService {

    List<Category> findAllCategories();
    Category createCategory(String name);
    Category renameCategory(Long id, String name);
    void deleteCategory(Long id);

    List<Mechanic> findAllMechanics();
    Mechanic createMechanic(String name);
    Mechanic renameMechanic(Long id, String name);
    void deleteMechanic(Long id);

    List<Publisher> findPublishers(PublisherStatus status); // status == null -> wszyscy
    Publisher createPublisher(String name);
    Publisher approvePublisher(Long id);

    List<Author> findAllAuthors();
    Author createAuthor(String firstName, String lastName);
    Author updateAuthor(Long id, String firstName, String lastName);
    void deleteAuthor(Long id);
}
