package pl.m22.gamehive.collection.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.m22.gamehive.collection.model.ExpansionCollectionItem;
import pl.m22.gamehive.collection.model.GameCollectionItem;
import pl.m22.gamehive.collection.repository.ExpansionCollectionItemRepository;
import pl.m22.gamehive.collection.repository.GameCollectionItemRepository;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.HashedPassword;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.repository.UserAuditLogRepository;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.service.UserService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test NIE jest @Transactional — AFTER_COMMIT odpala się tylko po faktycznym committcie.
 * Dlatego operuje na WŁASNYM, tymczasowym użytkowniku, a nie na fixture'owej Jane: skasowanie
 * zasianego usera byłoby trwałe dla całej bazy H2 i zepsułoby pozostałe testy w tym kontekście.
 */
@SpringBootTest
@ActiveProfiles("test")
class CollectionUserCleanupTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired GameCollectionItemRepository gameCollectionRepository;
    @Autowired ExpansionCollectionItemRepository expansionCollectionRepository;
    @Autowired GameRepository gameRepository;
    @Autowired GameExpansionRepository expansionRepository;
    @Autowired UserAuditLogRepository userAuditLogRepository;
    @Autowired PlatformTransactionManager txManager;
    @MockitoBean JavaMailSender mailSender;

    private static final Email ADMIN = new Email("john.doe@example.com");

    private UUID doomedUserId;

    @BeforeEach
    void setUp() {
        AppUser doomed = AppUser.register(
                new Username("doomed-collector"),
                new Email("doomed.collector@example.com"),
                HashedPassword.fromHash("{bcrypt}$2a$10$wnJKcfT8rFhyhno51MBqHeZS.ZYKXUavokV3EAQkq/WTd5E17V9fe"));
        doomedUserId = userRepository.saveAndFlush(doomed).getId();

        // gra 1 (Agricola) i dodatek 1 (Rzeka) — jedyne APPROVED cele; wpisy Jane na tych samych celach
        // zostają nietknięte, co dowodzi scope'owania sprzątania po userId
        gameCollectionRepository.saveAndFlush(
                new GameCollectionItem(doomedUserId, gameRepository.findById(1L).orElseThrow()));
        expansionCollectionRepository.saveAndFlush(
                new ExpansionCollectionItem(doomedUserId, expansionRepository.findById(1L).orElseThrow()));
    }

    @AfterEach
    void cleanup() {
        // po teście „szczęśliwej ścieżki" user i jego wpisy już nie istnieją; po pozostałych trzeba posprzątać.
        // Całość w jednej transakcji — poza nią remove() na odłączonej encji nie ma EntityManagera.
        new TransactionTemplate(txManager).executeWithoutResult(_ -> {
            gameCollectionRepository.deleteByUserId(doomedUserId);
            expansionCollectionRepository.deleteByUserId(doomedUserId);
            userRepository.findById(doomedUserId).ifPresent(userRepository::delete);
            // deleteUser zapisuje wpis DELETE w user_audit_log (brak FK, więc przeżywa usera)
            userAuditLogRepository.deleteAll(userAuditLogRepository.findByTargetId(doomedUserId));
        });
    }

    @Test
    @DisplayName("usunięcie konta -> AFTER_COMMIT kasuje wpisy kolekcji usera, cudze zostają")
    void deleteUser_removesOwnCollectionItems() {
        assertThat(gameCollectionRepository.findByUserId(doomedUserId, Pageable.unpaged())).hasSize(1);
        assertThat(expansionCollectionRepository.findByUserId(doomedUserId, Pageable.unpaged())).hasSize(1);

        userService.deleteUser(doomedUserId, ADMIN);

        assertThat(gameCollectionRepository.findByUserId(doomedUserId, Pageable.unpaged())).isEmpty();
        assertThat(expansionCollectionRepository.findByUserId(doomedUserId, Pageable.unpaged())).isEmpty();

        // wpisy Jane na tych samych celach nietknięte — sprzątanie jest scope'owane po userId
        assertThat(gameCollectionRepository.existsByUserIdAndGameId(SeededUsers.JANE_ID, 1L)).isTrue();
        assertThat(expansionCollectionRepository.existsByUserIdAndExpansionId(SeededUsers.JANE_ID, 1L)).isTrue();
    }

    @Test
    @DisplayName("nieudane usunięcie konta -> brak zdarzenia, kolekcja nietknięta")
    void failedDelete_keepsCollectionItems() {
        // próba usunięcia samego siebie -> CANNOT_MODIFY_OWN_ACCOUNT, transakcja nie commituje
        assertThat(userRepository.existsById(doomedUserId)).isTrue();

        try {
            userService.deleteUser(doomedUserId, new Email("doomed.collector@example.com"));
        } catch (RuntimeException expected) {
            // guard zadziałał — istotny jest stan po nim
        }

        assertThat(userRepository.existsById(doomedUserId)).isTrue();
        assertThat(gameCollectionRepository.findByUserId(doomedUserId, Pageable.unpaged())).hasSize(1);
        assertThat(expansionCollectionRepository.findByUserId(doomedUserId, Pageable.unpaged())).hasSize(1);
    }
}
