package pl.m22.gamehive.game.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.model.Game;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    // tytuł nie jest unikalny (model moderacji dopuszcza duplikaty), stąd List, nie Optional
    List<Game> findByTitle(String title);

    // dla APPROVED to cała globalna biblioteka — wyłącznie stronicowane
    Page<Game> findByModerationStatus(ModerationStatus moderationStatus, Pageable pageable);

    List<Game> findBySubmittedBy(UUID submittedBy);

    boolean existsByPublishersId(Long publishersId);

    boolean existsByCategoriesId(Long categoriesId);

    boolean existsByMechanicsId(Long mechanicsId);

    boolean existsByAuthorsId(Long authorsId);

    // „moje zgłoszenia": statusy DRAFT/PENDING/REJECTED danego użytkownika (GET /api/v1/games/mine)
    Page<Game> findBySubmittedByAndModerationStatusIn(UUID submittedBy,
                                                      Collection<ModerationStatus> statuses,
                                                      Pageable pageable);

}
