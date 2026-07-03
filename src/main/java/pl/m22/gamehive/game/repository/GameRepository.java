package pl.m22.gamehive.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.model.Game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    Optional<Game> findByTitle(String title);

    List<Game> findByModerationStatus(ModerationStatus moderationStatus);

    List<Game> findBySubmittedBy(UUID submittedBy);
}
