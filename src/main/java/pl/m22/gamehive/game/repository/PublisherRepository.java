package pl.m22.gamehive.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.game.model.Publisher;
import pl.m22.gamehive.game.model.TaxonomyStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface PublisherRepository extends JpaRepository<Publisher, Long> {

    Optional<Publisher> findByName(String name);

    List<Publisher> findByStatus(TaxonomyStatus status);

    boolean existsByName(String name);
}
