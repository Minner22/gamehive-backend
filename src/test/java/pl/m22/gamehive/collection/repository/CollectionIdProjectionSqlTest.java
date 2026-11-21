package pl.m22.gamehive.collection.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.support.CapturingStatementInspector;
import pl.m22.gamehive.support.SeededUsers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GH-142: odczyt identyfikatorów kolekcji ma czytać <b>jedną kolumnę jednej tabeli</b> — bez JOIN-a
 * do {@code games}/{@code game_expansions} i bez dociągania ich kolekcji. Wynik zapytania tego nie
 * pokaże (te same id wyjdą też z JOIN-a albo z mapowania encji), dlatego test sprawdza sam SQL.
 *
 * <p>Osobna klasa, bo inspektor SQL zmienia konfigurację {@code EntityManagerFactory}, a więc
 * i kontekst Springa — w pozostałych testach repozytoriów nie jest potrzebny.
 */
@DataJpaTest(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "pl.m22.gamehive.support.CapturingStatementInspector")
@ActiveProfiles("test")
class CollectionIdProjectionSqlTest {

    @Autowired
    private GameCollectionItemRepository gameCollectionRepository;

    @Autowired
    private ExpansionCollectionItemRepository expansionCollectionRepository;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void setUp() {
        em.clear();
        CapturingStatementInspector.clear();
    }

    @Test
    @DisplayName("findGameIdsByUserId -> jedno zapytanie, tylko game_collection_items, bez JOIN-a")
    void gameIds_singleStatementWithoutJoin() {
        List<Long> ids = gameCollectionRepository.findGameIdsByUserId(SeededUsers.JANE_ID);

        assertThat(ids).containsExactly(1L);
        assertSingleJoinFreeStatementOn("game_collection_items", "games");
    }

    @Test
    @DisplayName("findExpansionIdsByUserId -> jedno zapytanie, tylko expansion_collection_items, bez JOIN-a")
    void expansionIds_singleStatementWithoutJoin() {
        List<Long> ids = expansionCollectionRepository.findExpansionIdsByUserId(SeededUsers.JANE_ID);

        assertThat(ids).containsExactly(1L);
        assertSingleJoinFreeStatementOn("expansion_collection_items", "game_expansions");
    }

    private static void assertSingleJoinFreeStatementOn(String collectionTable, String targetTable) {
        List<String> statements = CapturingStatementInspector.statements();
        assertThat(statements).hasSize(1);

        String sql = statements.getFirst().toLowerCase();
        assertThat(sql)
                .contains(collectionTable)
                .doesNotContain(" join ")
                // nazwa tabeli celu nie może wystąpić jako osobny identyfikator (kolumna game_id jest OK)
                .doesNotContainPattern("\\b" + targetTable + "\\b");
    }
}
