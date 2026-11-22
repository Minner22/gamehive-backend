package pl.m22.gamehive.auth.jwt.service;

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
import pl.m22.gamehive.auth.jwt.config.AccessTokenProperties;
import pl.m22.gamehive.auth.jwt.config.ActivationTokenProperties;
import pl.m22.gamehive.auth.jwt.config.PasswordResetTokenProperties;
import pl.m22.gamehive.common.exception.BaseException;
import pl.m22.gamehive.common.exception.ErrorCode;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Awaria Redis w {@link RedisSessionEpochStore}: zapis → {@code REDIS_UNAVAILABLE}, odczyt → fail-open ({@code null}).
 * GH-165: każdy przypadek dla OBU postaci niedostępności ({@link pl.m22.gamehive.support.RedisUnavailability#failures()}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisSessionEpochStoreFailureTest {

    private static final String EMAIL = "user@test.com";

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock AccessTokenProperties accessProps;
    @Mock ActivationTokenProperties activationProps;
    @Mock PasswordResetTokenProperties passwordResetProps;

    private RedisSessionEpochStore store;

    @BeforeEach
    void setUp() {
        // konstrukcja ręczna zamiast @InjectMocks: dzięki temu accessProps/activationProps/passwordResetProps są
        // jawnie odczytane jako argumenty konstruktora (inaczej analiza widzi je jako nieużywane)
        store = new RedisSessionEpochStore(redisTemplate, accessProps, activationProps, passwordResetProps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")
    @DisplayName("Zapis każdej epoki (sesja, aktywacja, reset hasła) przy niedostępnym Redis -> REDIS_UNAVAILABLE")
    void setEpoch_redisUnavailable_throwsRedisUnavailable(RuntimeException failure) {
        doThrow(failure).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertRedisUnavailable(() -> store.invalidateNow(EMAIL));
        assertRedisUnavailable(() -> store.invalidateActivationNow(EMAIL));
        assertRedisUnavailable(() -> store.invalidatePasswordResetNow(EMAIL));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")
    @DisplayName("Odczyt każdej epoki przy niedostępnym Redis -> null (fail-open), bez wyjątku")
    void getEpoch_redisUnavailable_failsOpen(RuntimeException failure) {
        when(valueOps.get(anyString())).thenThrow(failure);

        assertThat(store.getInvalidAfter(EMAIL)).isNull();
        assertThat(store.getActivationInvalidAfter(EMAIL)).isNull();
        assertThat(store.getPasswordResetInvalidAfter(EMAIL)).isNull();
    }

    @Test
    @DisplayName("Inny błąd Redis (np. WRONGTYPE) przy odczycie -> NIE fail-open: wyjątek leci dalej (GH-165)")
    void getEpoch_otherRedisError_isNotSwallowed() {
        // fail-open dotyczy tylko niedostępności; błąd programistyczny nie może po cichu wyłączyć epok
        when(valueOps.get(anyString())).thenThrow(new RedisSystemException("WRONGTYPE", new RuntimeException()));

        assertThatThrownBy(() -> store.getInvalidAfter(EMAIL)).isInstanceOf(RedisSystemException.class);
    }

    private static void assertRedisUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.REDIS_UNAVAILABLE);
    }
}
