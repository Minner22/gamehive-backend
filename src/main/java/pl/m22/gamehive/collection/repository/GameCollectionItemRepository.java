package pl.m22.gamehive.collection.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.collection.model.GameCollectionItem;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GameCollectionItemRepository extends JpaRepository<GameCollectionItem, Long> {

    Page<GameCollectionItem> findByUserId(UUID userId, Pageable pageable);

    Optional<GameCollectionItem> findByUserIdAndGameId(UUID userId, Long gameId);

    boolean existsByUserIdAndGameId(UUID userId, Long gameId);

    @Modifying
    @Query("delete from GameCollectionItem c where c.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
