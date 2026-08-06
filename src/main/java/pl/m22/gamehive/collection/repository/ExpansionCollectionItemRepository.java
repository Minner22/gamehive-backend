package pl.m22.gamehive.collection.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.collection.model.ExpansionCollectionItem;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpansionCollectionItemRepository extends JpaRepository<ExpansionCollectionItem, Long> {

    Page<ExpansionCollectionItem> findByUserId(UUID userId, Pageable pageable);

    Optional<ExpansionCollectionItem> findByUserIdAndExpansionId(UUID userId, Long expansionId);

    boolean existsByUserIdAndExpansionId(UUID userId, Long expansionId);

    @Modifying
    @Query("delete from ExpansionCollectionItem c where c.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
