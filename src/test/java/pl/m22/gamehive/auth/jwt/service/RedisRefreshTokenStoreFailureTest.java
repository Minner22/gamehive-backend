package pl.m22.gamehive.auth.jwt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import pl.m22.gamehive.auth.jwt.config.RefreshTokenProperties;
import pl.m22.gamehive.common.exception.BaseException;
import pl.m22.gamehive.common.exception.ErrorCode;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * GH-165: każda operacja {@link RedisRefreshTokenStore} przy niedostępnym Redis kończy się
 * {@code REDIS_UNAVAILABLE} (503) — fail-closed, bo bez Redisa nie da się ani zapisać, ani zweryfikować, ani
 * unieważnić refresh tokena. Przed GH-165 timeout dawał tu nieprzetłumaczony wyjątek (500).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisRefreshTokenStoreFailureTest {

    private static final String EMAIL = "user@test.com";

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock ZSetOperations<String, String> zSetOps;
    @Mock RefreshTokenProperties refreshProps;

    private RedisRefreshTokenStore store;

    @BeforeEach
    void setUp() {
        store = new RedisRefreshTokenStore(redisTemplate, refreshProps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")
    @DisplayName("saveRefreshToken przy niedostępnym Redis -> REDIS_UNAVAILABLE")
    void save_redisUnavailable_throwsRedisUnavailable(RuntimeException failure) {
        doThrow(failure).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertRedisUnavailable(() -> store.saveRefreshToken("jti-1", EMAIL, Instant.now().plusSeconds(600)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")
    @DisplayName("existsByJti przy niedostępnym Redis -> REDIS_UNAVAILABLE (refresh nie przejdzie bez weryfikacji)")
    void exists_redisUnavailable_throwsRedisUnavailable(RuntimeException failure) {
        when(redisTemplate.hasKey(anyString())).thenThrow(failure);

        assertRedisUnavailable(() -> store.existsByJti("jti-1"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")
    @DisplayName("revokeAllByUserEmail przy niedostępnym Redis -> REDIS_UNAVAILABLE")
    void revoke_redisUnavailable_throwsRedisUnavailable(RuntimeException failure) {
        when(zSetOps.range(anyString(), anyLong(), anyLong())).thenThrow(failure);

        assertRedisUnavailable(() -> store.revokeAllByUserEmail(EMAIL));
    }

    private static void assertRedisUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.REDIS_UNAVAILABLE);
    }
}
