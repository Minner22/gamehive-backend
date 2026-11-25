package pl.m22.gamehive.user.service;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * GH-147: te same scenariusze na prawdziwym PostgreSQL 16 — blokada {@code SELECT … FOR UPDATE} i widoczność
 * zatwierdzonych zmian w READ COMMITTED zachowują się tu tak jak na produkcji, a nie jak na H2.
 * Tag {@code postgres}: job {@code integration} ({@code ./mvnw -Pintegration test}). Schemat z migracji, bez
 * {@code data.sql} — role z {@code V2}, innych adminów brak.
 */
@Tag("postgres")
@Testcontainers
@ActiveProfiles({"test", "it-postgres"})
class LastAdminConcurrencyPostgresTest extends AbstractLastAdminConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
}
