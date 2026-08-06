package pl.m22.gamehive.collection.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.collection.model.GameCollectionItem;
import pl.m22.gamehive.collection.model.OwnershipStatus;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.support.SeededUsers;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class GameCollectionItemRepositoryTest {

    @Autowired
    private GameCollectionItemRepository collectionRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("findByUserId -> tylko wpisy danego użytkownika (izolacja kolekcji)")
    void findByUserId_isolatesUsers() {
        Page<GameCollectionItem> jane = collectionRepository.findByUserId(SeededUsers.JANE_ID, Pageable.unpaged());
        Page<GameCollectionItem> john = collectionRepository.findByUserId(SeededUsers.JOHN_ID, Pageable.unpaged());

        assertThat(jane).extracting(item -> item.getGame().getTitle()).containsExactly("Agricola");
        assertThat(john).extracting(item -> item.getGame().getTitle()).containsExactly("Carcassonne");
        assertThat(collectionRepository.findByUserId(SeededUsers.MARK_ID, Pageable.unpaged())).isEmpty();
    }

    @Test
    @DisplayName("zasiany wpis -> ownershipStatus OWNED i relacja do gry")
    void seededItem_hasOwnedStatus() {
        GameCollectionItem item = collectionRepository
                .findByUserIdAndGameId(SeededUsers.JANE_ID, 1L).orElseThrow();

        assertThat(item.getOwnershipStatus()).isEqualTo(OwnershipStatus.OWNED);
        assertThat(item.getGame().getTitle()).isEqualTo("Agricola");
        assertThat(item.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("existsByUserIdAndGameId -> true dla własnego wpisu, false dla cudzego i dla nieobecnego")
    void existsGuard() {
        assertThat(collectionRepository.existsByUserIdAndGameId(SeededUsers.JANE_ID, 1L)).isTrue();
        assertThat(collectionRepository.existsByUserIdAndGameId(SeededUsers.JANE_ID, 7L)).isFalse();  // wpis Johna
        assertThat(collectionRepository.existsByUserIdAndGameId(SeededUsers.MARK_ID, 1L)).isFalse();
    }

    @Test
    @DisplayName("zapis nowego wpisu -> konstruktor ustawia OWNED, createdAt uzupełnia audyt encji")
    void saveAndRead() {
        Game carcassonne = gameRepository.findByTitle("Carcassonne").getFirst();

        Long id = collectionRepository
                .saveAndFlush(new GameCollectionItem(SeededUsers.MARK_ID, carcassonne)).getId();
        em.clear();

        GameCollectionItem reloaded = collectionRepository.findById(id).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo(SeededUsers.MARK_ID);
        assertThat(reloaded.getOwnershipStatus()).isEqualTo(OwnershipStatus.OWNED);
        assertThat(reloaded.getGame().getTitle()).isEqualTo("Carcassonne");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("konstruktor bez userId / bez gry -> NullPointerException (guard błędu programisty)")
    void constructor_rejectsNulls() {
        Game agricola = gameRepository.findByTitle("Agricola").getFirst();

        assertThatNullPointerException().isThrownBy(() -> new GameCollectionItem(null, agricola));
        assertThatNullPointerException().isThrownBy(() -> new GameCollectionItem(SeededUsers.MARK_ID, null));
    }

    @Test
    @DisplayName("duplikat (userId, gameId) -> naruszenie unikatu na poziomie bazy (druga linia obrony)")
    void duplicate_violatesUniqueConstraint() {
        Game agricola = gameRepository.findByTitle("Agricola").getFirst();

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                collectionRepository.saveAndFlush(new GameCollectionItem(SeededUsers.JANE_ID, agricola)));
    }

    @Test
    @DisplayName("ten sam cel u dwóch użytkowników -> dozwolone (unikat obejmuje parę, nie samą grę)")
    void sameGame_differentUsers_allowed() {
        Game agricola = gameRepository.findByTitle("Agricola").getFirst();

        collectionRepository.saveAndFlush(new GameCollectionItem(SeededUsers.MARK_ID, agricola));

        assertThat(collectionRepository.existsByUserIdAndGameId(SeededUsers.MARK_ID, 1L)).isTrue();
        assertThat(collectionRepository.existsByUserIdAndGameId(SeededUsers.JANE_ID, 1L)).isTrue();
    }

    @Test
    @DisplayName("hard-delete gry -> kaskada FK kasuje wpisy kolekcji (ON DELETE CASCADE, także w H2)")
    void cascade_hardDeleteGame_removesCollectionItem() {
        Game agricola = gameRepository.findByTitle("Agricola").getFirst();
        Long gameId = agricola.getId();
        assertThat(collectionRepository.existsByUserIdAndGameId(SeededUsers.JANE_ID, gameId)).isTrue();

        gameRepository.delete(agricola);
        em.flush();
        em.clear();

        Number remaining = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM game_collection_items WHERE game_id = :id")
                .setParameter("id", gameId)
                .getSingleResult();
        assertThat(remaining.longValue()).isZero();
        // wpis Johna (gra 7) nietknięty — kaskada dotyczy wyłącznie skasowanego celu
        assertThat(collectionRepository.existsByUserIdAndGameId(SeededUsers.JOHN_ID, 7L)).isTrue();
    }

    @Test
    @DisplayName("deleteByUserId -> znikają wszystkie wpisy usera, cudze zostają")
    void deleteByUserId() {
        collectionRepository.deleteByUserId(SeededUsers.JANE_ID);
        em.flush();

        assertThat(collectionRepository.findByUserId(SeededUsers.JANE_ID, Pageable.unpaged())).isEmpty();
        assertThat(collectionRepository.findByUserId(SeededUsers.JOHN_ID, Pageable.unpaged())).hasSize(1);
    }
}
