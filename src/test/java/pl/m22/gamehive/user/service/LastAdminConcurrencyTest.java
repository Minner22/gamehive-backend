package pl.m22.gamehive.user.service;

import org.springframework.test.context.ActiveProfiles;

/**
 * GH-147: scenariusze z {@link AbstractLastAdminConcurrencyTest} na H2 (zwykły {@code ./mvnw test}).
 */
@ActiveProfiles("test")
class LastAdminConcurrencyTest extends AbstractLastAdminConcurrencyTest {
}
