package pl.m22.gamehive.auth.jwt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.auth.jwt.JwtTokenType;

import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TokenBlacklistServiceImplTest {

    @Autowired TokenBlacklistService tokenBlacklistService;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("blacklistAccessToken + isBlacklisted -> true")
    void blacklist_and_check() {
        String token = jwtService.generateToken("test@test.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        String jti = jwtService.extractJtiFromToken(token);

        tokenBlacklistService.blacklistAccessToken(token);

        assertTrue(tokenBlacklistService.isBlacklisted(jti));
    }

    @Test
    @DisplayName("non-blacklisted JTI -> false")
    void not_blacklisted() {
        assertFalse(tokenBlacklistService.isBlacklisted("some-random-jti"));
    }

    @Test
    @DisplayName("isBlacklisted with null JTI -> false")
    void null_jti_returns_false() {
        assertFalse(tokenBlacklistService.isBlacklisted(null));
    }

    @Test
    @DisplayName("access token now has JTI")
    void access_token_has_jti() {
        String token = jwtService.generateToken("test@test.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        String jti = jwtService.extractJtiFromToken(token);
        assertNotNull(jti);
        assertFalse(jti.isEmpty());
    }
}
