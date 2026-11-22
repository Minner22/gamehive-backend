package pl.m22.gamehive.auth.jwt.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GH-165: awaria Redis w {@link TokenBlacklistServiceImpl}. Odczyt jest fail-open (inaczej każde żądanie
 * z poprawnym tokenem dostawało 401 przez {@code JwtAuthFilter}), zapis połyka awarię z logiem ERROR.
 * Oba zachowania dla OBU postaci niedostępności ({@link pl.m22.gamehive.support.RedisUnavailability#failures()}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenBlacklistServiceImplFailureTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private TokenBlacklistServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistServiceImpl(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")
    @DisplayName("isBlacklisted przy niedostępnym Redis -> false (fail-open), bez wyjątku")
    void isBlacklisted_redisUnavailable_failsOpen(RuntimeException failure) {
        when(redisTemplate.hasKey(anyString())).thenThrow(failure);

        assertThat(service.isBlacklisted("some-jti")).isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")
    @DisplayName("blacklistToken przy niedostępnym Redis -> awaria połknięta (log ERROR), bez wyjątku")
    void blacklistToken_redisUnavailable_isSwallowed(RuntimeException failure) throws Exception {
        String jti = UUID.randomUUID().toString();
        doThrow(failure).when(valueOps).set(eq("blacklist:" + jti), eq("1"), any(Duration.class));

        assertThatCode(() -> service.blacklistToken(signedToken(jti))).doesNotThrowAnyException();
        verify(valueOps).set(eq("blacklist:" + jti), eq("1"), any(Duration.class));   // naprawdę próbował zapisać
    }

    @Test
    @DisplayName("Inny błąd Redis (np. WRONGTYPE) w isBlacklisted -> NIE fail-open: wyjątek leci dalej")
    void isBlacklisted_otherRedisError_isNotSwallowed() {
        // decyzja GH-165: łapiemy tylko niedostępność, nie całe DataAccessException — błąd programistyczny
        // nie może po cichu i na stałe wyłączyć blacklisty
        when(redisTemplate.hasKey(anyString())).thenThrow(new RedisSystemException("WRONGTYPE", new RuntimeException()));

        assertThatThrownBy(() -> service.isBlacklisted("some-jti")).isInstanceOf(RedisSystemException.class);
    }

    private static String signedToken(String jti) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user@test.com")
                .jwtID(jti)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner("test-secret-0123456789abcdefghijklmnop"));
        return jwt.serialize();
    }
}
