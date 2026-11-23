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
    @DisplayName("saveRefreshToken -> klucz refresh_token:{jti} istnieje")
    void save_and_exists() {
        String jti = UUID.randomUUID().toString();
        store.saveRefreshToken(jti, "user@test.com", Instant.now().plusSeconds(3600));
        assertTrue(exists(jti));
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

        assertFalse(exists(jti1));
        assertFalse(exists(jti2));
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
        long surviving = Arrays.stream(jtis).filter(this::exists).count();
        assertEquals(5, surviving, "Cap powinien zostawić dokładnie 5 tokenów");
    }


    @Test
    @DisplayName("revokeAllByUserEmail for non-existent user does not throw")
    void revoke_nonexistent_user_no_error() {
        assertDoesNotThrow(() -> store.revokeAllByUserEmail("nobody@test.com"));
    }

    // GH-146: existsByJti usunięte z produkcji (zaproszenie do TOCTOU "sprawdź, potem użyj") — testy czytają klucz wprost
    private boolean exists(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("refresh_token:" + jti));
    }

    // ---------- GH-146: rotate = atomowa rotacja (Lua): konsumpcja + znacznik + następca ----------

    private static final String EMAIL = "rotate@test.com";

    private static Instant exp() {
        return Instant.now().plusSeconds(3600);
    }

    private RefreshTokenConsumeResult rotate(String oldJti, String newJti) {
        return store.rotate(oldJti, newJti, EMAIL, exp(), exp());
    }

    private static String newJti() {
        return UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("rotate aktywnego tokena -> CONSUMED: stary usunięty, znacznik z emailem i TTL, następca zapisany w tym samym kroku")
    void rotate_activeToken_consumedMarkedAndSuccessorStored() {
        String jti = newJti();
        String other = newJti();
        String successor = newJti();
        store.saveRefreshToken(jti, EMAIL, exp());
        store.saveRefreshToken(other, EMAIL, exp());

        assertThat(rotate(jti, successor)).isEqualTo(CONSUMED);

        assertThat(redisTemplate.hasKey("refresh_token:" + jti)).isFalse();
        assertThat(redisTemplate.opsForZSet().score("user_refresh_tokens:" + EMAIL, jti)).isNull();
        assertThat(redisTemplate.opsForValue().get("refresh_rotated:" + jti)).isEqualTo(EMAIL);
        assertThat(redisTemplate.getExpire("refresh_rotated:" + jti, TimeUnit.MILLISECONDS))
                .as("znacznik musi wygasać razem ze starym tokenem, nie żyć wiecznie")
                .isPositive().isLessThanOrEqualTo(3_600_000L).isGreaterThan(3_500_000L);
        // następca: klucz z emailem i TTL + wpis w ZSET
        assertThat(redisTemplate.opsForValue().get("refresh_token:" + successor)).isEqualTo(EMAIL);
        assertThat(redisTemplate.getExpire("refresh_token:" + successor, TimeUnit.MILLISECONDS))
                .isPositive().isLessThanOrEqualTo(3_600_000L).isGreaterThan(3_500_000L);
        assertThat(redisTemplate.opsForZSet().score("user_refresh_tokens:" + EMAIL, successor)).isNotNull();
        assertThat(redisTemplate.getExpire("user_refresh_tokens:" + EMAIL)).as("ZSET z TTL").isPositive();
        // pozostałe sesje użytkownika nietknięte
        assertThat(redisTemplate.hasKey("refresh_token:" + other)).isTrue();
        assertThat(redisTemplate.opsForZSet().zCard("user_refresh_tokens:" + EMAIL)).isEqualTo(2L);
    }

    @Test
    @DisplayName("drugi rotate tego samego JTI -> REUSED i NIE zapisuje drugiego następcy")
    void rotate_twice_reusedWithoutSecondSuccessor() {
        String jti = newJti();
        String first = newJti();
        String second = newJti();
        store.saveRefreshToken(jti, EMAIL, exp());

        assertThat(rotate(jti, first)).isEqualTo(CONSUMED);
        assertThat(rotate(jti, second)).isEqualTo(REUSED);

        assertThat(redisTemplate.hasKey("refresh_token:" + second)).isFalse();
        assertThat(redisTemplate.opsForZSet().score("user_refresh_tokens:" + EMAIL, second)).isNull();
    }

    @Test
    @DisplayName("token wypchnięty limitem 5 -> UNKNOWN, nie REUSED, bez znacznika i bez następcy")
    void rotate_evictedByLimit_unknown() {
        String[] jtis = new String[6];
        for (int i = 0; i < 6; i++) {
            jtis[i] = newJti();
            store.saveRefreshToken(jtis[i], EMAIL, exp());
        }
        String evicted = Arrays.stream(jtis)
                .filter(j -> !Boolean.TRUE.equals(redisTemplate.hasKey("refresh_token:" + j)))
                .findFirst().orElseThrow();
        String successor = newJti();

        assertThat(rotate(evicted, successor)).isEqualTo(UNKNOWN);
        assertThat(redisTemplate.hasKey("refresh_rotated:" + evicted)).isFalse();
        assertThat(redisTemplate.hasKey("refresh_token:" + successor)).isFalse();
    }

    @Test
    @DisplayName("token po revokeAllByUserEmail (logout) -> UNKNOWN")
    void rotate_afterRevokeAll_unknown() {
        String jti = newJti();
        store.saveRefreshToken(jti, EMAIL, exp());
        store.revokeAllByUserEmail(EMAIL);

        assertThat(rotate(jti, newJti())).isEqualTo(UNKNOWN);
    }

    @Test
    @DisplayName("nieznany JTI -> UNKNOWN i nic nie powstaje")
    void rotate_unknownJti_unknownWithoutWrites() {
        String successor = newJti();

        assertThat(rotate("ghost-jti", successor)).isEqualTo(UNKNOWN);
        assertThat(redisTemplate.hasKey("refresh_rotated:ghost-jti")).isFalse();
        assertThat(redisTemplate.hasKey("refresh_token:" + successor)).isFalse();
    }

    @Test
    @DisplayName("stary token wygasły -> UNKNOWN bez dotykania Redisa (token zostaje)")
    void rotate_expired_unknownWithoutTouchingRedis() {
        String jti = newJti();
        store.saveRefreshToken(jti, EMAIL, exp());

        assertThat(store.rotate(jti, newJti(), EMAIL, Instant.now().minusSeconds(1), exp())).isEqualTo(UNKNOWN);
        assertThat(redisTemplate.hasKey("refresh_token:" + jti)).isTrue();
        assertThat(redisTemplate.hasKey("refresh_rotated:" + jti)).isFalse();
    }

    @Test
    @DisplayName("łańcuch 6 rotacji -> w ZSET zostaje 1 sesja (limit liczy urządzenia, nie odświeżenia)")
    void rotate_chainOfRotations_keepsOneSession() {
        String current = newJti();
        store.saveRefreshToken(current, EMAIL, exp());

        for (int i = 0; i < 6; i++) {
            String next = newJti();
            assertThat(rotate(current, next)).isEqualTo(CONSUMED);
            current = next;
        }

        assertThat(redisTemplate.opsForZSet().zCard("user_refresh_tokens:" + EMAIL)).isEqualTo(1L);
        assertThat(redisTemplate.hasKey("refresh_token:" + current)).isTrue();
    }

    @Test
    @DisplayName("revokeAllByUserEmail usuwa też następcę z rotacji (security review GH-146: następca jest w ZSET od razu)")
    void revokeAll_afterRotate_removesSuccessor() {
        String jti = newJti();
        String successor = newJti();
        store.saveRefreshToken(jti, EMAIL, exp());
        rotate(jti, successor);

        store.revokeAllByUserEmail(EMAIL);

        assertThat(redisTemplate.hasKey("refresh_token:" + successor)).isFalse();
        assertThat(redisTemplate.hasKey("user_refresh_tokens:" + EMAIL)).isFalse();
        assertThat(rotate(successor, newJti())).isEqualTo(UNKNOWN);
    }

    @Test
    @DisplayName("revokeAllByUserEmail nie rusza tokenów innego użytkownika")
    void revokeAll_isScopedToUser() {
        String mine = newJti();
        String theirs = newJti();
        store.saveRefreshToken(mine, EMAIL, exp());
        store.saveRefreshToken(theirs, "other@test.com", exp());

        store.revokeAllByUserEmail(EMAIL);

        assertThat(redisTemplate.hasKey("refresh_token:" + mine)).isFalse();
        assertThat(redisTemplate.hasKey("refresh_token:" + theirs)).isTrue();
        assertThat(redisTemplate.opsForZSet().score("user_refresh_tokens:other@test.com", theirs)).isNotNull();
    }

    @Test
    @DisplayName("dwa równoległe rotate tego samego JTI -> dokładnie jeden CONSUMED i jeden REUSED; zapisany tylko następca zwycięzcy")
    void rotate_concurrent_exactlyOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 30; i++) {
                String jti = newJti();
                store.saveRefreshToken(jti, EMAIL, exp());
                CountDownLatch start = new CountDownLatch(1);
                List<String> successors = List.of(newJti(), newJti());

                List<Future<RefreshTokenConsumeResult>> results = new ArrayList<>();
                for (String successor : successors) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return rotate(jti, successor);
                    }));
                }
                start.countDown();

                List<RefreshTokenConsumeResult> outcome = new ArrayList<>();
                for (Future<RefreshTokenConsumeResult> f : results) {
                    outcome.add(f.get(5, TimeUnit.SECONDS));
                }
                assertThat(outcome).as("iteracja %d", i).containsExactlyInAnyOrder(CONSUMED, REUSED);
                String winner = successors.get(outcome.indexOf(CONSUMED));
                String loser = successors.get(outcome.indexOf(REUSED));
                assertThat(redisTemplate.hasKey("refresh_token:" + winner)).isTrue();
                assertThat(redisTemplate.hasKey("refresh_token:" + loser)).isFalse();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
