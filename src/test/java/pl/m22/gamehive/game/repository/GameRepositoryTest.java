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
import pl.m22.gamehive.game.model.*;
import pl.m22.gamehive.support.SeededUsers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DataJpaTest
@ActiveProfiles("test")
class GameRepositoryTest {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private PublisherRepository publisherRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MechanicRepository mechanicRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("findByTitle -> zasiana gra APPROVED z pełnymi relacjami i danymi recenzji")
    void findSeededApproved_withRelations() {
        Game game = gameRepository.findByTitle("Agricola").getFirst();

        assertThat(game.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(game.getSubmittedBy()).isEqualTo(SeededUsers.JANE_ID);
        assertThat(game.getReviewedBy()).isEqualTo(SeededUsers.MARK_ID);
        assertThat(game.getReviewedAt()).isNotNull();
        assertThat(game.getPublishers()).extracting(Publisher::getName)
                .containsExactlyInAnyOrder("Rio Grande Games", "Z-Man Games");
        assertThat(game.getCategories()).extracting(Category::getName).containsExactly("Strategy");
        assertThat(game.getMechanics()).extracting(Mechanic::getName).containsExactly("Worker Placement");
        assertThat(game.getAuthors()).extracting(Author::getLastName).containsExactly("Rosenberg");
    }

    @Test
    @DisplayName("findByModerationStatus(PENDING) -> gra oczekująca, bez danych recenzji, puste kolekcje opcjonalne")
    void findByModerationStatus_pending() {
        Page<Game> pending = gameRepository.findByModerationStatus(ModerationStatus.PENDING, Pageable.unpaged());

        assertThat(pending).extracting(Game::getTitle).containsExactly("Pandemic");

        Game pandemic = pending.getContent().getFirst();
        assertThat(pandemic.getReviewedBy()).isNull();
        assertThat(pandemic.getReviewedAt()).isNull();
        assertThat(pandemic.getRejectionReason()).isNull();
        assertThat(pandemic.getMechanics()).isEmpty();
        assertThat(pandemic.getAuthors()).isEmpty();
    }

    @Test
    @DisplayName("gra REJECTED -> zachowuje rejectionReason i resubmissionCount")
    void rejectedGame_workflowFields() {
        Game rejected = gameRepository.findByTitle("Odrzucona Gra").getFirst();

        assertThat(rejected.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Duplikat istniejącej gry");
        assertThat(rejected.getResubmissionCount()).isEqualTo(1);
        assertThat(rejected.getReviewedBy()).isEqualTo(SeededUsers.MARK_ID);
    }

    @Test
    @DisplayName("findBySubmittedBy -> gry danego użytkownika; pusto dla nieznanego UUID")
    void findBySubmittedBy() {
        assertThat(gameRepository.findBySubmittedBy(SeededUsers.JANE_ID))
                .extracting(Game::getTitle)
                .containsExactlyInAnyOrder("Agricola", "Pandemic", "Szkic Jane", "Odrzucona Jane", "Limit Jane");

        assertThat(gameRepository.findBySubmittedBy(SeededUsers.UNKNOWN_ID)).isEmpty();
    }

    @Test
    @DisplayName("zapis nowej gry z relacjami -> odczyt zachowuje pola, relacje i domyślne wartości workflow")
    void saveAndRead_withRelations() {
        Publisher publisher = publisherRepository.findByName("Rio Grande Games").orElseThrow();
        Category category = categoryRepository.findByName("Party").orElseThrow();
        Mechanic mechanic = mechanicRepository.findByName("Dice Rolling").orElseThrow();
        Author author = authorRepository.findByFirstNameAndLastName("Reiner", "Knizia").orElseThrow();

        Game game = Game.builder()
                .title("Nowa Gra")
                .description("Opis nowej gry.")
                .submittedBy(SeededUsers.JANE_ID)
                .minPlayers(2)
                .maxPlayers(5)
                .playingTimeMinutes(60)
                .yearPublished(2024)
                .minAge(10)
                .coverImageUrl("https://example.com/nowa-gra.jpg")
                .build();
        game.addPublisher(publisher);
        game.addCategory(category);
        game.addMechanic(mechanic);
        game.addAuthor(author);

        Long id = gameRepository.saveAndFlush(game).getId();
        em.clear();

        Game reloaded = gameRepository.findById(id).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Nowa Gra");
        assertThat(reloaded.getMinPlayers()).isEqualTo(2);
        assertThat(reloaded.getMaxPlayers()).isEqualTo(5);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(reloaded.getSubmittedBy()).isEqualTo(SeededUsers.JANE_ID);
        assertThat(reloaded.getResubmissionCount()).isZero();
        assertThat(reloaded.getReviewedBy()).isNull();
        assertThat(reloaded.getReviewedAt()).isNull();
        assertThat(reloaded.getPublishers()).extracting(Publisher::getName).containsExactly("Rio Grande Games");
        assertThat(reloaded.getCategories()).extracting(Category::getName).containsExactly("Party");
        assertThat(reloaded.getMechanics()).extracting(Mechanic::getName).containsExactly("Dice Rolling");
        assertThat(reloaded.getAuthors()).extracting(Author::getLastName).containsExactly("Knizia");
    }

    @Test
    @DisplayName("builder z moderationStatus(DRAFT) -> zapis i odczyt zachowują status DRAFT")
    void saveDraft_keepsDraftStatus() {
        Game draft = Game.builder()
                .title("Szkic Gry")
                .description("Niedokończone zgłoszenie.")
                .submittedBy(SeededUsers.JANE_ID)
                .moderationStatus(ModerationStatus.DRAFT)
                .minPlayers(1)
                .maxPlayers(2)
                .playingTimeMinutes(30)
                .yearPublished(2025)
                .minAge(8)
                .build();

        Long id = gameRepository.saveAndFlush(draft).getId();
        em.clear();

        assertThat(gameRepository.findById(id).orElseThrow().getModerationStatus())
                .isEqualTo(ModerationStatus.DRAFT);
    }

    @Test
    @DisplayName("builder z moderationStatus(APPROVED) -> IllegalArgumentException (stan początkowy tylko DRAFT/PENDING)")
    void builder_approvedInitialStatus_rejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> Game.builder()
                .title("Nielegalna Gra")
                .description("Próba utworzenia od razu jako APPROVED.")
                .submittedBy(SeededUsers.JANE_ID)
                .moderationStatus(ModerationStatus.APPROVED)
                .build());
    }

    @Test
    @DisplayName("findBySubmittedByAndModerationStatusIn -> zgłoszenia użytkownika bez APPROVED")
    void findMySubmissions_excludesApproved() {
        Page<Game> mine = gameRepository.findBySubmittedByAndModerationStatusIn(
                SeededUsers.JANE_ID,
                Set.of(ModerationStatus.DRAFT, ModerationStatus.PENDING, ModerationStatus.REJECTED),
                Pageable.unpaged());

        assertThat(mine).extracting(Game::getTitle)
                .containsExactlyInAnyOrder("Pandemic", "Szkic Jane", "Odrzucona Jane", "Limit Jane");
    }

    @Test
    @DisplayName("submitForModeration() na DRAFT -> PENDING, resubmissionCount bez zmian")
    void submitForModeration_draftToPending() {
        Game draft = gameRepository.findByTitle("Szkic Jane").getFirst();

        draft.submitForModeration();
        em.flush();
        em.clear();

        Game reloaded = gameRepository.findByTitle("Szkic Jane").getFirst();
        assertThat(reloaded.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(reloaded.getResubmissionCount()).isZero();
    }

    @Test
    @DisplayName("resubmit() na REJECTED -> PENDING, count+1, wyczyszczone dane recenzji")
    void resubmit_rejectedToPending() {
        Game rejected = gameRepository.findByTitle("Odrzucona Jane").getFirst();

        rejected.resubmit();
        em.flush();
        em.clear();

        Game reloaded = gameRepository.findByTitle("Odrzucona Jane").getFirst();
        assertThat(reloaded.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(reloaded.getResubmissionCount()).isEqualTo(2);
        assertThat(reloaded.getReviewedBy()).isNull();
        assertThat(reloaded.getReviewedAt()).isNull();
        assertThat(reloaded.getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("updateDetails + clearAssociations -> edycja pól i pełna wymiana relacji")
    void updateDetails_replacesFieldsAndAssociations() {
        Game draft = gameRepository.findByTitle("Szkic Jane").getFirst();
        Publisher zMan = publisherRepository.findByName("Z-Man Games").orElseThrow();
        Category coop = categoryRepository.findByName("Cooperative").orElseThrow();

        draft.updateDetails("Szkic Jane v2", "Poprawiony opis.", 2, 6, 45, 2025, 10, null);
        draft.clearAssociations();
        draft.addPublisher(zMan);
        draft.addCategory(coop);
        em.flush();
        em.clear();

        Game reloaded = gameRepository.findByTitle("Szkic Jane v2").getFirst();
        assertThat(reloaded.getDescription()).isEqualTo("Poprawiony opis.");
        assertThat(reloaded.getMaxPlayers()).isEqualTo(6);
        assertThat(reloaded.getModerationStatus()).isEqualTo(ModerationStatus.DRAFT);
        assertThat(reloaded.getPublishers()).extracting(Publisher::getName).containsExactly("Z-Man Games");
        assertThat(reloaded.getCategories()).extracting(Category::getName).containsExactly("Cooperative");
        assertThat(reloaded.getMechanics()).isEmpty();
        assertThat(reloaded.getAuthors()).isEmpty();
    }

    @Test
    @DisplayName("usunięcie gry -> znikają wpisy w tabeli łączącej, słowniki zostają")
    void deleteGame_removesJoinRows_keepsDictionaries() {
        Game game = gameRepository.findByTitle("Agricola").getFirst();
        Long id = game.getId();
        long publishersBefore = publisherRepository.count();

        gameRepository.delete(game);
        em.flush();

        Number joinRows = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM game_publisher WHERE game_id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(joinRows.longValue()).isZero();
        assertThat(gameRepository.findByTitle("Agricola")).isEmpty();
        assertThat(publisherRepository.count()).isEqualTo(publishersBefore);
    }
}