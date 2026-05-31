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

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RedisRefreshTokenStoreTest {

    @Autowired RedisRefreshTokenStore store;
    @Autowired CacheManager cacheManager;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @BeforeEach
    void clearCache() {
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
}
