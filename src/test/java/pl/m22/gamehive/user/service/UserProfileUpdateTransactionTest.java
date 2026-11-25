package pl.m22.gamehive.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.repository.UserRepository;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * GH-150: {@code updateCurrentUserProfile} działa w transakcji SERWISU (zapisowej). Bez {@code @Transactional}
 * odczyt i zapis szły w osobnych transakcjach repozytorium, a zdarzenie AFTER_COMMIT opublikowane kiedyś z tej
 * metody nigdy by nie dotarło (ta pułapka już raz kosztowała: force-logout).
 *
 * <p>Test sprawdza NAZWĘ transakcji i brak read-only, a nie samo {@code isActualTransactionActive()}: spy stoi przed
 * proxy repozytorium (bez adnotacji widzi {@code null}), ale gdyby kiedyś stanął za nim, zobaczyłby transakcję
 * read-only z {@code SimpleJpaRepository} — sama flaga by ją przepuściła. Klasa celowo BEZ {@code @Transactional}
 * — transakcja testu zamaskowałaby brak transakcji serwisu.
 * Odpowiedź spy'a przerywa metodę wyjątkiem, więc profil Jane w współdzielonej bazie zostaje nietknięty.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserProfileUpdateTransactionTest {

    private static final Email JANE = new Email("jane.smith@example.com");

    @Autowired UserService userService;
    @MockitoSpyBean UserRepository userRepository;
    @MockitoBean JavaMailSender mailSender;

    @Test
    @DisplayName("GH-150: odczyt użytkownika biegnie w zapisowej transakcji updateCurrentUserProfile, nie w transakcji repozytorium")
    void updateCurrentUserProfile_runsInServiceReadWriteTransaction() {
        AtomicReference<String> txName = new AtomicReference<>();
        AtomicReference<Boolean> readOnly = new AtomicReference<>();
        doAnswer(_ -> {
            txName.set(TransactionSynchronizationManager.getCurrentTransactionName());
            readOnly.set(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            throw new StopHere();
        }).when(userRepository).findByEmail(any(Email.class));

        assertThatThrownBy(() -> userService.updateCurrentUserProfile(JANE, profileUpdate()))
                .isInstanceOf(StopHere.class);

        assertThat(txName.get()).as("nazwa transakcji widziana w findByEmail")
                .endsWith("UserServiceImpl.updateCurrentUserProfile");
        assertThat(readOnly.get()).as("transakcja read-only").isFalse();
    }

    private static UserProfileUpdateDto profileUpdate() {
        return new UserProfileUpdateDto("Jane", null, null, null, null, null);
    }

    private static final class StopHere extends RuntimeException {
    }
}
