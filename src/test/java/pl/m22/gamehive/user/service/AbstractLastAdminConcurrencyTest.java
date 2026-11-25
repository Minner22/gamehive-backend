package pl.m22.gamehive.user.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.HashedPassword;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.common.exception.BaseException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * GH-147: guard ostatniego admina pod równoczesnymi żądaniami. Dwóch adminów X i Y działa na sobie nawzajem w tej
 * samej chwili — bez serializacji oba żądania widzą licznik 2, oba przechodzą i system zostaje bez admina.
 *
 * <p><b>Wyścig jest deterministyczny</b>: spy na {@code countByRoles_NameAndEnabledTrue} woła prawdziwe zapytanie,
 * a potem czeka na barierze dwóch stron (z krótkim timeoutem).
 * <ul>
 *   <li>Bez blokady oba wątki policzą adminów, zanim którykolwiek zatwierdzi zmianę → bariera puszcza oba → oba
 *       się udają (to odtwarza błąd).</li>
 *   <li>Z blokadą wiersza {@code ROLE_ADMIN} drugi wątek czeka na {@code SELECT … FOR UPDATE}, ZANIM policzy;
 *       pierwszy po timeoucie bariery idzie dalej i commituje; drugi dostaje blokadę, liczy 1 →
 *       {@code CANNOT_REMOVE_LAST_ADMIN}.</li>
 * </ul>
 * Timeout bariery jest krótszy niż domyślny timeout blokady H2, żeby czekający wątek nie dostał wyjątku blokady.
 *
 * <p>Bez {@code @Transactional} (transakcja testu zamaskowałaby współbieżność); własni użytkownicy, sprzątani
 * w {@code @AfterEach}. Pozostali aktywni admini (na H2: John z {@code data.sql}) tracą {@code ROLE_ADMIN} na czas
 * testu i dostają ją z powrotem — inaczej zawsze byłby trzeci admin i wyścigu nie dałoby się odtworzyć.
 * Te same scenariusze biegną na H2 ({@link LastAdminConcurrencyTest}) i na PostgreSQL
 * ({@link LastAdminConcurrencyPostgresTest}).
 */
@SpringBootTest
abstract class AbstractLastAdminConcurrencyTest {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String PASSWORD = "secret123";
    private static final long BARRIER_TIMEOUT_MS = 300;

    private static final Email ADMIN_X = new Email("gh147_admin_x@test.com");
    private static final Email ADMIN_Y = new Email("gh147_admin_y@test.com");
    private static final Email USER_1 = new Email("gh147_user_1@test.com");
    private static final Email USER_2 = new Email("gh147_user_2@test.com");
    private static final List<Email> OWN_USERS = List.of(ADMIN_X, ADMIN_Y, USER_1, USER_2);

    @Autowired UserService userService;
    @Autowired TransactionTemplate tx;
    @MockitoSpyBean UserRepository userRepository;
    @MockitoSpyBean UserRoleRepository userRoleRepository;
    @MockitoBean JavaMailSender mailSender;

    private final List<UUID> demotedAdmins = new ArrayList<>();
    private UUID adminX;
    private UUID adminY;
    private UUID user1;
    private UUID user2;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(_ -> {
            demoteOtherActiveAdmins();
            adminX = createUser("gh147_admin_x", ADMIN_X, ROLE_ADMIN, ROLE_USER);
            adminY = createUser("gh147_admin_y", ADMIN_Y, ROLE_ADMIN, ROLE_USER);
            user1 = createUser("gh147_user_1", USER_1, ROLE_USER);
            user2 = createUser("gh147_user_2", USER_2, ROLE_USER);
        });

        // spy na repozytorium Spring Data to proxy interfejsu: callRealMethod() nie działa (metoda abstrakcyjna),
        // a Spring nie ustawia spiedInstance — prawdziwe wywołanie robi domyślna, delegująca odpowiedź spy'a
        Answer<?> delegateToRepository = mockingDetails(userRepository).getMockCreationSettings().getDefaultAnswer();
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object count = delegateToRepository.answer(invocation);
            try {
                barrier.await(BARRIER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | BrokenBarrierException _) {
                // drugi wątek czeka na blokadzie (poprawka działa) albo bariera już pękła — idziemy dalej
            }
            return count;
        }).when(userRepository).countByRoles_NameAndEnabledTrue(anyString());
        clearInvocations(userRoleRepository);
    }

    @AfterEach
    void tearDown() {
        reset(userRepository, userRoleRepository);
        tx.executeWithoutResult(_ -> {
            OWN_USERS.forEach(email -> userRepository.findByEmail(email).ifPresent(userRepository::delete));
            UserRole admin = role(ROLE_ADMIN);
            demotedAdmins.forEach(id -> userRepository.findById(id).ifPresent(user -> {
                Set<UserRole> roles = new HashSet<>(user.getRoles());
                roles.add(admin);
                user.replaceRoles(roles);
                userRepository.save(user);
            }));
        });
        demotedAdmins.clear();
    }

    @Test
    @DisplayName("X usuwa Y ∥ Y usuwa X przy dwóch adminach -> dokładnie jedno się udaje, drugie CANNOT_REMOVE_LAST_ADMIN")
    void concurrentCrossDelete_exactlyOneSucceeds() throws Exception {
        List<Outcome> outcomes = race(
                () -> userService.deleteUser(adminY, ADMIN_X),
                () -> userService.deleteUser(adminX, ADMIN_Y));

        assertExactlyOneBlocked(outcomes);
    }

    @Test
    @DisplayName("X deaktywuje Y ∥ Y deaktywuje X -> dokładnie jedno się udaje")
    void concurrentCrossDeactivate_exactlyOneSucceeds() throws Exception {
        List<Outcome> outcomes = race(
                () -> userService.deactivateUser(adminY, ADMIN_X),
                () -> userService.deactivateUser(adminX, ADMIN_Y));

        assertExactlyOneBlocked(outcomes);
    }

    @Test
    @DisplayName("X odbiera ROLE_ADMIN Y ∥ Y odbiera ROLE_ADMIN X -> dokładnie jedno się udaje")
    void concurrentCrossDemote_exactlyOneSucceeds() throws Exception {
        Set<String> userOnly = Set.of(ROLE_USER);

        List<Outcome> outcomes = race(
                () -> userService.updateUserRoles(adminY, userOnly, ADMIN_X),
                () -> userService.updateUserRoles(adminX, userOnly, ADMIN_Y));

        assertExactlyOneBlocked(outcomes);
    }

    @Test
    @DisplayName("X usuwa własne konto ∥ Y usuwa własne konto -> dokładnie jedno się udaje")
    void concurrentOwnAccountDeletion_exactlyOneSucceeds() throws Exception {
        List<Outcome> outcomes = race(
                () -> userService.deleteOwnAccount(ADMIN_X, PASSWORD),
                () -> userService.deleteOwnAccount(ADMIN_Y, PASSWORD));

        assertExactlyOneBlocked(outcomes);
    }

    @Test
    @DisplayName("Równoległe usunięcie dwóch zwykłych użytkowników -> oba przechodzą, bez blokady wiersza ROLE_ADMIN")
    void concurrentRegularUserDeletion_notSerialized() throws Exception {
        List<Outcome> outcomes = race(
                () -> userService.deleteUser(user1, ADMIN_X),
                () -> userService.deleteUser(user2, ADMIN_X));

        assertThat(outcomes).containsOnly(Outcome.SUCCESS);
        verify(userRoleRepository, never()).findByNameForUpdate(anyString());
    }

    private void assertExactlyOneBlocked(List<Outcome> outcomes) {
        assertThat(outcomes)
                .as("jedno żądanie przechodzi, drugie trafia na guard")
                .containsExactlyInAnyOrder(Outcome.SUCCESS, Outcome.blocked(ErrorCode.CANNOT_REMOVE_LAST_ADMIN));

        reset(userRepository);   // bez bariery
        assertThat(userRepository.countByRoles_NameAndEnabledTrue(ROLE_ADMIN))
                .as("aktywni admini po wyścigu")
                .isEqualTo(1);
    }

    private List<Outcome> race(Runnable first, Runnable second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> a = pool.submit(() -> attempt(start, first));
            Future<Outcome> b = pool.submit(() -> attempt(start, second));
            start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private static Outcome attempt(CountDownLatch start, Runnable action) throws InterruptedException {
        start.await();
        try {
            action.run();
            return Outcome.SUCCESS;
        } catch (BaseException e) {
            return Outcome.blocked(e.getErrorCode());
        }
    }

    private void demoteOtherActiveAdmins() {
        UserRole admin = role(ROLE_ADMIN);
        userRepository.findAll().stream()
                .filter(AppUser::isEnabled)
                .filter(user -> user.getRoles().contains(admin))
                .forEach(user -> {
                    Set<UserRole> roles = new HashSet<>(user.getRoles());
                    roles.remove(admin);
                    user.replaceRoles(roles);
                    userRepository.save(user);
                    demotedAdmins.add(user.getId());
                });
    }

    private UUID createUser(String username, Email email, String... roleNames) {
        AppUser user = AppUser.register(new Username(username), email, HashedPassword.fromHash("{noop}" + PASSWORD));
        user.activate();
        Set<UserRole> roles = new HashSet<>();
        for (String name : roleNames) {
            roles.add(role(name));
        }
        user.replaceRoles(roles);
        return userRepository.save(user).getId();
    }

    private UserRole role(String name) {
        return userRoleRepository.findByName(name).orElseThrow();
    }

    private record Outcome(boolean success, ErrorCode errorCode) {

        static final Outcome SUCCESS = new Outcome(true, null);

        static Outcome blocked(ErrorCode code) {
            return new Outcome(false, code);
        }
    }
}
