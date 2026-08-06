package pl.m22.gamehive.game.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.model.Category;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.model.Mechanic;
import pl.m22.gamehive.support.SeededUsers;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class GameExpansionRepositoryTest {

    @Autowired
    private GameExpansionRepository expansionRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MechanicRepository mechanicRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("zasiany dodatek APPROVED -> relacja do gry bazowej i dane recenzji")
    void findSeededApproved_withBaseGame() {
        GameExpansion expansion = expansionRepository.findByName("Carcassonne: Rzeka").getFirst();

        assertThat(expansion.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(expansion.getBaseGame().getTitle()).isEqualTo("Carcassonne");
        assertThat(expansion.getSubmittedBy()).isEqualTo(SeededUsers.JANE_ID);
        assertThat(expansion.getReviewedBy()).isEqualTo(SeededUsers.MARK_ID);
        assertThat(expansion.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("nadpisanie vs dziedziczenie: własne maxPlayers/kategorie, reszta z gry bazowej")
    void effectiveValues_mixOfOverrideAndInheritance() {
        GameExpansion expansion = expansionRepository.findByName("Carcassonne: Rzeka").getFirst();

        // nadpisane
        assertThat(expansion.getMaxPlayers()).isEqualTo(6);
        assertThat(expansion.getEffectiveMaxPlayers()).isEqualTo(6);
        assertThat(expansion.getCategories()).extracting(Category::getName).containsExactly("Expansion Only");
        assertThat(expansion.getEffectiveCategories()).extracting(Category::getName).containsExactly("Expansion Only");

        // dziedziczone (własne == null / puste)
        assertThat(expansion.getMinPlayers()).isNull();
        assertThat(expansion.getEffectiveMinPlayers()).isEqualTo(2);            // gra 7: 2..2
        assertThat(expansion.getPlayingTimeMinutes()).isNull();
        assertThat(expansion.getEffectivePlayingTimeMinutes()).isEqualTo(45);
        assertThat(expansion.getMinAge()).isNull();
        assertThat(expansion.getEffectiveMinAge()).isEqualTo(8);
        assertThat(expansion.getMechanics()).isEmpty();
        assertThat(expansion.getEffectiveMechanics()).extracting(Mechanic::getName).containsExactly("Area Control");
    }

    @Test
    @DisplayName("dodatek bez żadnych nadpisań -> wszystkie wartości efektywne pochodzą z gry bazowej")
    void effectiveValues_pureInheritance() {
        GameExpansion expansion = expansionRepository.findByName("Carcassonne: Karczmy").getFirst();

        assertThat(expansion.getEffectiveMinPlayers()).isEqualTo(2);
        assertThat(expansion.getEffectiveMaxPlayers()).isEqualTo(2);
        assertThat(expansion.getEffectivePlayingTimeMinutes()).isEqualTo(45);
        assertThat(expansion.getEffectiveMinAge()).isEqualTo(8);
        assertThat(expansion.getEffectiveCategories()).extracting(Category::getName).containsExactly("Family");
        assertThat(expansion.getEffectiveMechanics()).extracting(Mechanic::getName).containsExactly("Area Control");
    }

    @Test
    @DisplayName("findByModerationStatus(PENDING) -> jedyny oczekujący dodatek, bez danych recenzji")
    void findByModerationStatus_pending() {
        Page<GameExpansion> pending =
                expansionRepository.findByModerationStatus(ModerationStatus.PENDING, Pageable.unpaged());

        assertThat(pending).extracting(GameExpansion::getName).containsExactly("Carcassonne: Karczmy");
        assertThat(pending.getContent().getFirst().getReviewedBy()).isNull();
        assertThat(pending.getContent().getFirst().getReviewedAt()).isNull();
        assertThat(pending.getContent().getFirst().getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("dodatek REJECTED -> zachowuje rejectionReason i resubmissionCount")
    void rejectedExpansion_workflowFields() {
        GameExpansion rejected = expansionRepository.findByName("Limit Dodatku Jane").getFirst();

        assertThat(rejected.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Wielokrotnie odrzucane");
        assertThat(rejected.getResubmissionCount()).isEqualTo(2);
        assertThat(rejected.getReviewedBy()).isEqualTo(SeededUsers.MARK_ID);
    }

    @Test
    @DisplayName("findBySubmittedBy -> dodatki danego użytkownika; pusto dla nieznanego UUID")
    void findBySubmittedBy() {
        assertThat(expansionRepository.findBySubmittedBy(SeededUsers.JANE_ID))
                .extracting(GameExpansion::getName)
                .containsExactlyInAnyOrder("Carcassonne: Rzeka", "Carcassonne: Karczmy",
                        "Szkic Dodatku Jane", "Odrzucony Dodatek Jane", "Limit Dodatku Jane");

        assertThat(expansionRepository.findBySubmittedBy(SeededUsers.UNKNOWN_ID)).isEmpty();
    }

    @Test
    @DisplayName("findBySubmittedByAndModerationStatusIn -> własne dodatki bez APPROVED")
    void findMySubmissions_excludesApproved() {
        Page<GameExpansion> mine = expansionRepository.findBySubmittedByAndModerationStatusIn(
                SeededUsers.JANE_ID,
                Set.of(ModerationStatus.DRAFT, ModerationStatus.PENDING, ModerationStatus.REJECTED),
                Pageable.unpaged());

        assertThat(mine).extracting(GameExpansion::getName).containsExactlyInAnyOrder(
                "Carcassonne: Karczmy", "Szkic Dodatku Jane", "Odrzucony Dodatek Jane", "Limit Dodatku Jane");
    }

    @Test
    @DisplayName("zapis nowego dodatku -> odczyt zachowuje nadpisania, relacje i domyślne pola workflow")
    void saveAndRead_withOverrides() {
        Game baseGame = gameRepository.findByTitle("Carcassonne").getFirst();
        Category category = categoryRepository.findByName("Strategy").orElseThrow();
        Mechanic mechanic = mechanicRepository.findByName("Dice Rolling").orElseThrow();

        GameExpansion expansion = GameExpansion.builder()
                .baseGame(baseGame)
                .name("Nowy Dodatek")
                .description("Opis nowego dodatku.")
                .submittedBy(SeededUsers.JANE_ID)
                .maxPlayers(8)
                .minAge(14)
                .build();
        expansion.addCategory(category);
        expansion.addMechanic(mechanic);

        Long id = expansionRepository.saveAndFlush(expansion).getId();
        em.clear();

        GameExpansion reloaded = expansionRepository.findById(id).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);   // domyślny status buildera
        assertThat(reloaded.getResubmissionCount()).isZero();
        assertThat(reloaded.getReviewedBy()).isNull();
        assertThat(reloaded.getBaseGame().getTitle()).isEqualTo("Carcassonne");
        assertThat(reloaded.getMaxPlayers()).isEqualTo(8);
        assertThat(reloaded.getMinPlayers()).isNull();
        assertThat(reloaded.getEffectiveMinPlayers()).isEqualTo(2);
        assertThat(reloaded.getEffectiveMinAge()).isEqualTo(14);
        assertThat(reloaded.getCategories()).extracting(Category::getName).containsExactly("Strategy");
        assertThat(reloaded.getMechanics()).extracting(Mechanic::getName).containsExactly("Dice Rolling");
    }

    @Test
    @DisplayName("builder z moderationStatus(DRAFT) -> zapis i odczyt zachowują status DRAFT")
    void saveDraft_keepsDraftStatus() {
        GameExpansion draft = GameExpansion.builder()
                .baseGame(gameRepository.findByTitle("Carcassonne").getFirst())
                .name("Szkic Dodatku")
                .description("Niedokończone zgłoszenie dodatku.")
                .submittedBy(SeededUsers.JANE_ID)
                .moderationStatus(ModerationStatus.DRAFT)
                .build();

        Long id = expansionRepository.saveAndFlush(draft).getId();
        em.clear();

        assertThat(expansionRepository.findById(id).orElseThrow().getModerationStatus())
                .isEqualTo(ModerationStatus.DRAFT);
    }

    @Test
    @DisplayName("builder z moderationStatus(APPROVED) -> IllegalArgumentException (stan początkowy tylko DRAFT/PENDING)")
    void builder_approvedInitialStatus_rejected() {
        Game baseGame = gameRepository.findByTitle("Carcassonne").getFirst();

        assertThatIllegalArgumentException().isThrownBy(() -> GameExpansion.builder()
                .baseGame(baseGame)
                .name("Nielegalny Dodatek")
                .description("Próba utworzenia od razu jako APPROVED.")
                .submittedBy(SeededUsers.JANE_ID)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());
    }

    @Test
    @DisplayName("builder bez gry bazowej -> NullPointerException (guard błędu programisty, jak submittedBy)")
    void builder_missingBaseGame_rejected() {
        assertThatNullPointerException().isThrownBy(() -> GameExpansion.builder()
                .name("Dodatek bez bazy")
                .description("Próba utworzenia bez gry bazowej.")
                .submittedBy(SeededUsers.JANE_ID)
                .build());
    }

    @Test
    @DisplayName("resubmit() na REJECTED -> PENDING, count+1, wyczyszczone dane recenzji")
    void resubmit_rejectedToPending() {
        GameExpansion rejected = expansionRepository.findByName("Odrzucony Dodatek Jane").getFirst();

        rejected.resubmit();
        em.flush();
        em.clear();

        GameExpansion reloaded = expansionRepository.findByName("Odrzucony Dodatek Jane").getFirst();
        assertThat(reloaded.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(reloaded.getResubmissionCount()).isEqualTo(2);
        assertThat(reloaded.getReviewedBy()).isNull();
        assertThat(reloaded.getReviewedAt()).isNull();
        assertThat(reloaded.getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("updateDetails + clearAssociations -> edycja pól i pełna wymiana własnych relacji")
    void updateDetails_replacesFieldsAndAssociations() {
        GameExpansion draft = expansionRepository.findByName("Szkic Dodatku Jane").getFirst();
        Category strategy = categoryRepository.findByName("Strategy").orElseThrow();

        draft.updateDetails("Szkic Dodatku Jane v2", "Poprawiony opis.", 3, 5, 90, 12);
        draft.clearAssociations();
        draft.addCategory(strategy);
        em.flush();
        em.clear();

        GameExpansion reloaded = expansionRepository.findByName("Szkic Dodatku Jane v2").getFirst();
        assertThat(reloaded.getDescription()).isEqualTo("Poprawiony opis.");
        assertThat(reloaded.getEffectiveMinPlayers()).isEqualTo(3);
        assertThat(reloaded.getEffectiveMaxPlayers()).isEqualTo(5);
        assertThat(reloaded.getModerationStatus()).isEqualTo(ModerationStatus.DRAFT);
        assertThat(reloaded.getCategories()).extracting(Category::getName).containsExactly("Strategy");
        assertThat(reloaded.getMechanics()).isEmpty();
    }

    @Test
    @DisplayName("usunięcie dodatku -> znikają wpisy expansion_category, słowniki i gra bazowa zostają")
    void deleteExpansion_removesJoinRows_keepsDictionariesAndBaseGame() {
        GameExpansion expansion = expansionRepository.findByName("Carcassonne: Rzeka").getFirst();
        Long id = expansion.getId();
        long categoriesBefore = categoryRepository.count();

        expansionRepository.delete(expansion);
        em.flush();

        Number joinRows = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM expansion_category WHERE expansion_id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(joinRows.longValue()).isZero();
        assertThat(expansionRepository.findByName("Carcassonne: Rzeka")).isEmpty();
        assertThat(categoryRepository.count()).isEqualTo(categoriesBefore);
        assertThat(gameRepository.findByTitle("Carcassonne")).hasSize(1);
    }

    @Test
    @DisplayName("existsByBaseGameId / existsByCategoriesId / existsByMechanicsId -> guardy usuwania")
    void existsGuards() {
        Long carcassonneId = gameRepository.findByTitle("Carcassonne").getFirst().getId();
        Long agricolaId = gameRepository.findByTitle("Agricola").getFirst().getId();

        assertThat(expansionRepository.existsByBaseGameId(carcassonneId)).isTrue();
        assertThat(expansionRepository.existsByBaseGameId(agricolaId)).isFalse();
        assertThat(expansionRepository.existsByCategoriesId(5L)).isTrue();    // Expansion Only
        assertThat(expansionRepository.existsByCategoriesId(3L)).isFalse();   // Party — musi zostać usuwalna
        assertThat(expansionRepository.existsByMechanicsId(1L)).isFalse();
    }
}
