package pl.m22.gamehive.auth.jwt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class RedisSessionEpochStoreTest {

    private static final String EMAIL = "epoch_store@test.com";

    @Autowired
    RedisSessionEpochStore store;

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
}
