package pl.m22.gamehive.auth.jwt.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.auth.jwt.JwtTokenType;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TokenBlacklistServiceImplTest {

    private static final String BLACKLIST_TEST_SECRET = "test-blacklist-secret-abcdefghijklmnopqrstuvwxyz123456";

    @Autowired TokenBlacklistService tokenBlacklistService;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void cleanRedis() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("blacklistToken + isBlacklisted -> true")
    void blacklist_and_check() {
        String token = jwtService.generateToken("test@test.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        String jti = jwtService.extractJtiFromToken(token);

        tokenBlacklistService.blacklistToken(token);

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

    @Test
    @DisplayName("blacklistToken() dla tokenu bez expirationTime -> brak wpisu (gałąź exp == null)")
    void blacklist_token_without_expiration_noop() throws JOSEException {
        String jti = UUID.randomUUID().toString();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test@test.com")
                .jwtID(jti)
                .build();                          // ma JTI, ale brak expirationTime
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(BLACKLIST_TEST_SECRET.getBytes(StandardCharsets.UTF_8)));

        tokenBlacklistService.blacklistToken(jwt.serialize());

        assertFalse(tokenBlacklistService.isBlacklisted(jti));
    }
}
