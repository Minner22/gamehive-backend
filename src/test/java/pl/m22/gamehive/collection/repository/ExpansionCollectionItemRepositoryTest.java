package pl.m22.gamehive.collection.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.collection.model.ExpansionCollectionItem;
import pl.m22.gamehive.collection.model.OwnershipStatus;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.support.SeededUsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@ActiveProfiles("test")
class ExpansionCollectionItemRepositoryTest {

    @Autowired
    private ExpansionCollectionItemRepository collectionRepository;

    @Autowired
    private GameExpansionRepository expansionRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("findByUserId -> dodatek w kolekcji Jane, mimo że gry bazowej (7) w niej nie ma")
    void expansionIsIndependentOfBaseGame() {
        assertThat(collectionRepository.findByUserId(SeededUsers.JANE_ID, Pageable.unpaged()))
                .extracting(item -> item.getExpansion().getName())
                .containsExactly("Carcassonne: Rzeka");

        ExpansionCollectionItem item = collectionRepository
                .findByUserIdAndExpansionId(SeededUsers.JANE_ID, 1L).orElseThrow();
        assertThat(item.getOwnershipStatus()).isEqualTo(OwnershipStatus.OWNED);
        assertThat(item.getExpansion().getBaseGame().getId()).isEqualTo(7L);
        assertThat(item.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("existsByUserIdAndExpansionId -> true dla własnego wpisu, false dla użytkownika bez kolekcji")
    void existsGuard() {
        assertThat(collectionRepository.existsByUserIdAndExpansionId(SeededUsers.JANE_ID, 1L)).isTrue();
        assertThat(collectionRepository.existsByUserIdAndExpansionId(SeededUsers.MARK_ID, 1L)).isFalse();
    }

    @Test
    @DisplayName("zapis nowego wpisu -> konstruktor ustawia OWNED i relację do dodatku")
    void saveAndRead() {
        GameExpansion rzeka = expansionRepository.findByName("Carcassonne: Rzeka").getFirst();

        Long id = collectionRepository
                .saveAndFlush(new ExpansionCollectionItem(SeededUsers.MARK_ID, rzeka)).getId();
        em.clear();

        ExpansionCollectionItem reloaded = collectionRepository.findById(id).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo(SeededUsers.MARK_ID);
        assertThat(reloaded.getOwnershipStatus()).isEqualTo(OwnershipStatus.OWNED);
        assertThat(reloaded.getExpansion().getName()).isEqualTo("Carcassonne: Rzeka");
    }

    @Test
    @DisplayName("duplikat (userId, expansionId) -> naruszenie unikatu na poziomie bazy")
    void duplicate_violatesUniqueConstraint() {
        GameExpansion rzeka = expansionRepository.findByName("Carcassonne: Rzeka").getFirst();

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                collectionRepository.saveAndFlush(new ExpansionCollectionItem(SeededUsers.JANE_ID, rzeka)));
    }

    @Test
    @DisplayName("hard-delete dodatku -> kaskada FK kasuje wpisy kolekcji, gra bazowa zostaje")
    void cascade_hardDeleteExpansion_removesCollectionItem() {
        GameExpansion rzeka = expansionRepository.findByName("Carcassonne: Rzeka").getFirst();
        Long expansionId = rzeka.getId();

        expansionRepository.delete(rzeka);
        em.flush();
        em.clear();

        Number remaining = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM expansion_collection_items WHERE expansion_id = :id")
                .setParameter("id", expansionId)
                .getSingleResult();
        assertThat(remaining.longValue()).isZero();
        assertThat(gameRepository.findByTitle("Carcassonne")).hasSize(1);
    }

    @Test
    @DisplayName("deleteByUserId -> znikają wpisy usera")
    void deleteByUserId() {
        collectionRepository.deleteByUserId(SeededUsers.JANE_ID);
        em.flush();

        assertThat(collectionRepository.findByUserId(SeededUsers.JANE_ID, Pageable.unpaged())).isEmpty();
    }
}
