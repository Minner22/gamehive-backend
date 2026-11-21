package pl.m22.gamehive.auth.jwt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisSessionEpochStoreTest {

    private static final String EMAIL = "epoch_store@test.com";

    @Autowired
    RedisSessionEpochStore store;

    @Autowired
    RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("getActivationInvalidAfter() bez ustawionej epoki -> null")
    void getActivationInvalidAfter_notSet_returnsNull() {

        assertThat(store.getActivationInvalidAfter("never_set@test.com")).isNull();
    }

    @Test
    @DisplayName("invalidateActivationNow() -> zapisuje epokę uciętą do pełnych sekund")
    void invalidateActivationNow_storesSecondAlignedEpoch() {

        long beforeSec = Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();

        store.invalidateActivationNow(EMAIL);

        Long stored = store.getActivationInvalidAfter(EMAIL);
        assertThat(stored).isNotNull();
        // epoka jest wyrównana do pełnej sekundy (brak składowej ms) — to chroni świeży token przed rewokacją
        assertThat(stored % 1000).isZero();
        // i nie wcześniejsza niż początek sekundy sprzed wywołania
        assertThat(stored).isGreaterThanOrEqualTo(beforeSec);
    }

    @Test
    @DisplayName("epoka aktywacji i sesji są niezależne (różne klucze Redis)")
    void activationAndSessionEpochsAreIndependent() {

        store.invalidateActivationNow(EMAIL);

        // ustawienie epoki aktywacji nie tworzy epoki sesji dla tego samego maila
        assertThat(store.getInvalidAfter(EMAIL)).isNull();
        assertThat(store.getActivationInvalidAfter(EMAIL)).isNotNull();
    }

    // ---------- epoka resetu hasła (GH-145) ----------

    @Test
    @DisplayName("getPasswordResetInvalidAfter() bez ustawionej epoki -> null")
    void getPasswordResetInvalidAfter_notSet_returnsNull() {

        assertThat(store.getPasswordResetInvalidAfter("never_set_reset@test.com")).isNull();
    }

    @Test
    @DisplayName("invalidatePasswordResetNow() -> epoka ucięta do pełnych sekund, TTL = ważność PASSWORD_RESET (900 s)")
    void invalidatePasswordResetNow_storesSecondAlignedEpochWithResetTtl() {

        String email = "epoch_reset@test.com";
        long beforeSec = Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();

        store.invalidatePasswordResetNow(email);

        Long stored = store.getPasswordResetInvalidAfter(email);
        assertThat(stored).isNotNull();
        // bez ms: token wydany zaraz po epoce (iat ucięty do sekund) ma iat == epoka i przechodzi ostre "<"
        assertThat(stored % 1000).isZero();
        assertThat(stored).isGreaterThanOrEqualTo(beforeSec);

        // TTL z jwt.passwordreset.validityInSeconds (900 w application-test.yml), nie z ACCESS/ACTIVATION (2 s)
        Long ttl = redisTemplate.getExpire("password_reset_invalid_after:" + email, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(890L, 900L);
    }

    @Test
    @DisplayName("epoka resetu hasła jest niezależna od epoki sesji i aktywacji")
    void passwordResetEpochIsIndependentOfOtherEpochs() {

        String email = "epoch_reset_indep@test.com";

        store.invalidatePasswordResetNow(email);

        assertThat(store.getPasswordResetInvalidAfter(email)).isNotNull();
        assertThat(store.getInvalidAfter(email)).isNull();
        assertThat(store.getActivationInvalidAfter(email)).isNull();
    }
}
