package pl.m22.gamehive.game.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.model.GameExpansion;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface GameExpansionRepository extends JpaRepository<GameExpansion, Long>, JpaSpecificationExecutor<GameExpansion> {

    List<GameExpansion> findByName(String name);

    Page<GameExpansion> findByModerationStatus(ModerationStatus moderationStatus, Pageable pageable);

    List<GameExpansion> findBySubmittedBy(UUID submittedBy);

    Page<GameExpansion> findBySubmittedByAndModerationStatusIn(UUID submittedBy, Collection<ModerationStatus> statuses, Pageable pageable);

    boolean existsByBaseGameId(Long baseGameId);

    List<GameExpansion> findByBaseGameIdAndModerationStatus(Long baseGameId, ModerationStatus moderationStatus);

    boolean existsByCategoriesId(Long categoriesId);

    boolean existsByMechanicsId(Long mechanicsId);
}
