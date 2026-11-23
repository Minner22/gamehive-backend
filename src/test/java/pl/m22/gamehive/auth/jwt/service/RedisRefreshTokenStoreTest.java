package pl.m22.gamehive.auth.jwt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.auth.jwt.RefreshTokenConsumeResult;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static pl.m22.gamehive.auth.jwt.RefreshTokenConsumeResult.*;

@SpringBootTest
@ActiveProfiles("test")
class RedisRefreshTokenStoreTest {

    @Autowired RedisRefreshTokenStore store;
    @Autowired CacheManager cacheManager;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void resetState() {

        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();

        Cache c = cacheManager.getCache("userAuthState");
        if (c != null) {
            c.clear();
        }
    }

    @Test
    @DisplayName("saveRefreshToken + existsByJti -> true")
    void save_and_exists() {
        String jti = UUID.randomUUID().toString();
        store.saveRefreshToken(jti, "user@test.com", Instant.now().plusSeconds(3600));
        assertTrue(store.existsByJti(jti));
    }

    @Test
    @DisplayName("existsByJti for unknown JTI -> false")
    void unknown_jti_not_found() {
        assertFalse(store.existsByJti("non-existent-jti"));
    }

    @Test
    @DisplayName("revokeAllByUserEmail removes all tokens for user")
    void revoke_all_removes_tokens() {
        String jti1 = UUID.randomUUID().toString();
        String jti2 = UUID.randomUUID().toString();
        Instant exp = Instant.now().plusSeconds(3600);

        store.saveRefreshToken(jti1, "user@test.com", exp);
        store.saveRefreshToken(jti2, "user@test.com", exp);

        store.revokeAllByUserEmail("user@test.com");

        assertFalse(store.existsByJti(jti1));
        assertFalse(store.existsByJti(jti2));
    }

    @Test
    @DisplayName("max active tokens eviction - cap is enforced when limit exceeded")
    void evicts_oldest_when_exceeded() {
        String email = "evict@test.com";
        Instant exp = Instant.now().plusSeconds(3600);
        String[] jtis = new String[6];

        for (int i = 0; i < 6; i++) {
            jtis[i] = UUID.randomUUID().toString();
            store.saveRefreshToken(jtis[i], email, exp);
        }

        // max to 5 — po 6. zapisie dokładnie jeden token musi zostać eksmitowany
        long surviving = Arrays.stream(jtis).filter(store::existsByJti).count();
        assertEquals(5, surviving, "Cap powinien zostawić dokładnie 5 tokenów");
    }


    @Test
    @DisplayName("revokeAllByUserEmail for non-existent user does not throw")
    void revoke_nonexistent_user_no_error() {
        assertDoesNotThrow(() -> store.revokeAllByUserEmail("nobody@test.com"));
    }

    // ---------- GH-146: consume = atomowa rotacja (Lua) ----------

    private static final String EMAIL = "rotate@test.com";

    @Test
    @DisplayName("consume aktywnego tokena -> CONSUMED: klucz i wpis ZSET usunięte, znacznik refresh_rotated z emailem i TTL")
    void consume_activeToken_consumedAndMarked() {
        String jti = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        Instant exp = Instant.now().plusSeconds(3600);
        store.saveRefreshToken(jti, EMAIL, exp);
        store.saveRefreshToken(other, EMAIL, exp);

        assertThat(store.consume(jti, EMAIL, exp)).isEqualTo(CONSUMED);

        assertThat(redisTemplate.hasKey("refresh_token:" + jti)).isFalse();
        assertThat(redisTemplate.opsForZSet().score("user_refresh_tokens:" + EMAIL, jti)).isNull();
        assertThat(redisTemplate.opsForValue().get("refresh_rotated:" + jti)).isEqualTo(EMAIL);
        Long pttl = redisTemplate.getExpire("refresh_rotated:" + jti, TimeUnit.MILLISECONDS);
        assertThat(pttl).as("znacznik musi wygasać razem ze starym tokenem, nie żyć wiecznie")
                .isPositive().isLessThanOrEqualTo(3_600_000L).isGreaterThan(3_500_000L);
        // pozostałe sesje użytkownika nietknięte
        assertThat(redisTemplate.hasKey("refresh_token:" + other)).isTrue();
        assertThat(redisTemplate.opsForZSet().score("user_refresh_tokens:" + EMAIL, other)).isNotNull();
    }

    @Test
    @DisplayName("drugi consume tego samego JTI -> REUSED (token już zrotowany)")
    void consume_twice_reused() {
        String jti = UUID.randomUUID().toString();
        Instant exp = Instant.now().plusSeconds(3600);
        store.saveRefreshToken(jti, EMAIL, exp);

        assertThat(store.consume(jti, EMAIL, exp)).isEqualTo(CONSUMED);
        assertThat(store.consume(jti, EMAIL, exp)).isEqualTo(REUSED);
    }

    @Test
    @DisplayName("token wypchnięty limitem 5 -> UNKNOWN, nie REUSED (wypchnięcie nie może wylogować wszędzie)")
    void consume_evictedByLimit_unknown() {
        Instant exp = Instant.now().plusSeconds(3600);
        String[] jtis = new String[6];
        for (int i = 0; i < 6; i++) {
            jtis[i] = UUID.randomUUID().toString();
            store.saveRefreshToken(jtis[i], EMAIL, exp);
        }
        String evicted = Arrays.stream(jtis)
                .filter(j -> !Boolean.TRUE.equals(redisTemplate.hasKey("refresh_token:" + j)))
                .findFirst().orElseThrow();

        assertThat(store.consume(evicted, EMAIL, exp)).isEqualTo(UNKNOWN);
        assertThat(redisTemplate.hasKey("refresh_rotated:" + evicted)).isFalse();
    }

    @Test
    @DisplayName("token po revokeAllByUserEmail (logout) -> UNKNOWN")
    void consume_afterRevokeAll_unknown() {
        String jti = UUID.randomUUID().toString();
        Instant exp = Instant.now().plusSeconds(3600);
        store.saveRefreshToken(jti, EMAIL, exp);
        store.revokeAllByUserEmail(EMAIL);

        assertThat(store.consume(jti, EMAIL, exp)).isEqualTo(UNKNOWN);
    }

    @Test
    @DisplayName("nieznany JTI -> UNKNOWN i żaden znacznik nie powstaje")
    void consume_unknownJti_unknownWithoutMarker() {
        assertThat(store.consume("ghost-jti", EMAIL, Instant.now().plusSeconds(3600))).isEqualTo(UNKNOWN);
        assertThat(redisTemplate.hasKey("refresh_rotated:ghost-jti")).isFalse();
    }

    @Test
    @DisplayName("expiresAt w przeszłości -> UNKNOWN bez dotykania Redisa (token zostaje)")
    void consume_expired_unknownWithoutTouchingRedis() {
        String jti = UUID.randomUUID().toString();
        store.saveRefreshToken(jti, EMAIL, Instant.now().plusSeconds(3600));

        assertThat(store.consume(jti, EMAIL, Instant.now().minusSeconds(1))).isEqualTo(UNKNOWN);
        assertThat(redisTemplate.hasKey("refresh_token:" + jti)).isTrue();
        assertThat(redisTemplate.hasKey("refresh_rotated:" + jti)).isFalse();
    }

    @Test
    @DisplayName("łańcuch 6 rotacji -> w ZSET zostaje 1 sesja (limit liczy urządzenia, nie odświeżenia)")
    void consume_chainOfRotations_keepsOneSession() {
        Instant exp = Instant.now().plusSeconds(3600);
        String current = UUID.randomUUID().toString();
        store.saveRefreshToken(current, EMAIL, exp);

        for (int i = 0; i < 6; i++) {
            String next = UUID.randomUUID().toString();
            assertThat(store.consume(current, EMAIL, exp)).isEqualTo(CONSUMED);
            store.saveRefreshToken(next, EMAIL, exp);
            current = next;
        }

        assertThat(redisTemplate.opsForZSet().zCard("user_refresh_tokens:" + EMAIL)).isEqualTo(1L);
        assertThat(redisTemplate.hasKey("refresh_token:" + current)).isTrue();
    }

    @Test
    @DisplayName("dwa równoległe consume tego samego JTI -> dokładnie jeden CONSUMED i jeden REUSED (atomowość skryptu)")
    void consume_concurrent_exactlyOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 30; i++) {
                String jti = UUID.randomUUID().toString();
                Instant exp = Instant.now().plusSeconds(3600);
                store.saveRefreshToken(jti, EMAIL, exp);
                CountDownLatch start = new CountDownLatch(1);

                List<Future<RefreshTokenConsumeResult>> results = new ArrayList<>();
                for (int t = 0; t < 2; t++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return store.consume(jti, EMAIL, exp);
                    }));
                }
                start.countDown();

                List<RefreshTokenConsumeResult> outcome = new ArrayList<>();
                for (Future<RefreshTokenConsumeResult> f : results) {
                    outcome.add(f.get(5, TimeUnit.SECONDS));
                }
                assertThat(outcome).as("iteracja %d", i).containsExactlyInAnyOrder(CONSUMED, REUSED);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
