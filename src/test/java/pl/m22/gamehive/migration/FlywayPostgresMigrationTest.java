package pl.m22.gamehive.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GH-154: migracje Flyway na prawdziwym PostgreSQL — to, czego zielony {@code ./mvnw test} (H2, schemat z encji,
 * Flyway wyłączony) nie dowodzi.
 *
 * <ul>
 *   <li><b>Sam start kontekstu jest głównym testem</b>: wszystkie {@code V*} stosują się na PUSTEJ bazie, a
 *       {@code ddl-auto=validate} porównuje każdą encję ze schematem z migracji (nazwy i typy kolumn, tabele
 *       łączące) — rozjazd = kontekst nie wstaje.</li>
 *   <li>Asercje SQL pinują to, co istnieje TYLKO w migracjach i czego {@code validate} nie sprawdza: reguły
 *       {@code ON DELETE} i indeksy.</li>
 * </ul>
 *
 * <p>Tag {@code postgres}: wykluczony z domyślnego {@code ./mvnw test} (nie wymaga Dockera), uruchamiany przez
 * {@code ./mvnw -Pintegration test} (job {@code integration} w CI). Celowo BEZ
 * {@code @Testcontainers(disabledWithoutDocker = true)} — brak Dockera w CI ma czerwienić job, nie go pomijać.
 *
 * <p>Obraz = ten z {@code docker-compose.yml} (dev); przy podbiciu PostgreSQL zmień oba miejsca.
 */
@Tag("postgres")
@Testcontainers
@SpringBootTest
@ActiveProfiles({"test", "it-postgres"})
class FlywayPostgresMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mailSender;

    @Test
    @DisplayName("Działa na PostgreSQL, nie na H2 (sanity check konfiguracji profilu it-postgres)")
    void runsOnPostgres() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 16");
    }

    @Test
    @DisplayName("Wszystkie migracje V* zastosowane od zera, bez oczekujących i bez błędów (liczba z classpath, nie literał)")
    void allMigrationsApplied() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(flyway.info().pending()).as("migracje oczekujące").isEmpty();
        assertThat(applied).as("zastosowane = pliki V* na classpath").hasSize(files.length);
        assertThat(Arrays.stream(applied).map(MigrationInfo::getState))
                .as("stan każdej migracji")
                .containsOnly(MigrationState.SUCCESS);
    }

    @ParameterizedTest(name = "{0}.{1} -> {2}")
    @CsvSource({
            // V8: tabele łączące gry — kasowanie gry sprząta wpisy
            "game_publisher, game_id, CASCADE",
            "game_category, game_id, CASCADE",
            "game_mechanic, game_id, CASCADE",
            "game_author, game_id, CASCADE",
            // V8/V11: strona słownika BEZ kaskady — druga linia obrony guardów *_IN_USE
            "game_category, category_id, NO ACTION",
            "game_publisher, publisher_id, NO ACTION",
            // V11: dodatek NIE kaskaduje z gry — druga linia obrony GAME_HAS_EXPANSIONS
            "game_expansions, base_game_id, NO ACTION",
            "expansion_category, expansion_id, CASCADE",
            "expansion_mechanic, expansion_id, CASCADE",
            // V12: kolekcje sprzątane przy hard-delete celu
            "game_collection_items, game_id, CASCADE",
            "expansion_collection_items, expansion_id, CASCADE",
            // V1/V3: role użytkownika
            "user_roles, user_id, CASCADE",
            "user_roles, role_id, CASCADE"
    })
    @DisplayName("Reguły ON DELETE z migracji")
    void foreignKeyDeleteRules(String table, String column, String expectedRule) {
        List<String> rules = jdbc.queryForList("""
                select rc.delete_rule
                from information_schema.referential_constraints rc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = rc.constraint_name
                 and kcu.constraint_schema = rc.constraint_schema
                where kcu.table_schema = 'public' and kcu.table_name = ? and kcu.column_name = ?
                """, String.class, table, column);

        assertThat(rules).as("FK %s.%s", table, column).containsExactly(expectedRule);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            // V8 — m.in. indeksy, które commit d7a981d dopisał do już zastosowanego V8 (dev stał 5 tygodni na V8)
            "idx_games_moderation_status",
            "idx_games_submitted_by",
            "idx_game_publisher_publisher_id",
            "idx_game_category_category_id",
            "idx_game_mechanic_mechanic_id",
            "idx_game_author_author_id",
            // V11, V12
            "idx_game_expansions_base_game_id",
            "idx_game_collection_items_user_id",
            "idx_expansion_collection_items_user_id",
            // V4, V10 — audyty
            "idx_user_audit_log_target_id",
            "idx_cma_target_id"
    })
    @DisplayName("Indeksy z migracji istnieją")
    void indexesExist(String indexName) {
        Integer count = jdbc.queryForObject(
                "select count(*) from pg_indexes where schemaname = 'public' and indexname = ?", Integer.class, indexName);

        assertThat(count).as("indeks %s", indexName).isEqualTo(1);
    }
}
