package pl.m22.gamehive.auth.jwt.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import pl.m22.gamehive.auth.jwt.RefreshTokenConsumeResult;
import pl.m22.gamehive.auth.jwt.config.RefreshTokenProperties;
import pl.m22.gamehive.common.exception.BaseException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.logging.LoggingUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("revokeAllByUserEmail przy niedostępnym Redis -> REDIS_UNAVAILABLE")
    void revoke_redisUnavailable_throwsRedisUnavailable(RuntimeException failure) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(failure);

        assertRedisUnavailable(() -> store.revokeAllByUserEmail(EMAIL));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")
    @DisplayName("GH-146: rotate przy niedostępnym Redis -> REDIS_UNAVAILABLE (bez rotacji nie ma nowego tokena)")
    void rotate_redisUnavailable_throwsRedisUnavailable(RuntimeException failure) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(failure);

        assertRedisUnavailable(this::rotate);
    }

    @Test
    @DisplayName("GH-146: błąd skryptu (RedisSystemException) NIE jest maskowany jako REDIS_UNAVAILABLE")
    void rotate_scriptError_propagates() {
        RedisSystemException scriptError = new RedisSystemException("ERR Error running script", null);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(scriptError);

        assertThatThrownBy(this::rotate).isSameAs(scriptError);
    }

    @Test
    @DisplayName("GH-146: skrypt zwrócił null (np. w pipeline/MULTI) -> UNKNOWN, nie NPE")
    void rotate_nullResult_unknown() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(null);

        assertThat(rotate()).isEqualTo(RefreshTokenConsumeResult.UNKNOWN);
    }

    // ---------- GH-150: log ERROR przy awarii Redis nie zawiera pełnego e-maila ----------

    @Test
    @DisplayName("GH-150: saveRefreshToken przy awarii Redis -> log ERROR z ZAMASKOWANYM e-mailem")
    void save_redisUnavailable_logsObfuscatedEmail() {
        doThrow(new RedisConnectionFailureException("down")).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertLogMasksEmail(() -> store.saveRefreshToken("jti-1", EMAIL, Instant.now().plusSeconds(600)),
                "saving refresh token");
    }

    @Test
    @DisplayName("GH-150: revokeAllByUserEmail przy awarii Redis -> log ERROR z ZAMASKOWANYM e-mailem")
    void revoke_redisUnavailable_logsObfuscatedEmail() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new QueryTimeoutException("timeout"));

        assertLogMasksEmail(() -> store.revokeAllByUserEmail(EMAIL), "revoking tokens");
    }

    @Test
    @DisplayName("GH-150: rotate przy awarii Redis -> log ERROR z ZAMASKOWANYM e-mailem")
    void rotate_redisUnavailable_logsObfuscatedEmail() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertLogMasksEmail(this::rotate, "consuming refresh token");
    }

    private static void assertLogMasksEmail(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String messageFragment) {
        Logger storeLogger = (Logger) LoggerFactory.getLogger(RedisRefreshTokenStore.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        storeLogger.addAppender(appender);
        try {
            assertRedisUnavailable(call);
        } finally {
            storeLogger.detachAppender(appender);
        }

        assertThat(appender.list)
                .filteredOn(e -> e.getLevel() == Level.ERROR)
                .singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage())
                        .contains(messageFragment)
                        .contains(LoggingUtils.obfuscateEmail(EMAIL))
                        .doesNotContain(EMAIL));
    }

    private RefreshTokenConsumeResult rotate() {
        return store.rotate("jti-1", "jti-2", EMAIL, Instant.now().plusSeconds(600), Instant.now().plusSeconds(600));
    }

    private static void assertRedisUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.REDIS_UNAVAILABLE);
    }
}
