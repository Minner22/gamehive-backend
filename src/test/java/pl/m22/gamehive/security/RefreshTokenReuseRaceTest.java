package pl.m22.gamehive.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.RedisRefreshTokenStore;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.user.service.UserService;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Security review GH-146: dwa równoległe {@code /refresh} z tym samym skradzionym tokenem nie mogą zostawić
 * atakującemu działającej sesji, gdy drugi z nich wykrył ponowne użycie i wylogował użytkownika wszędzie.
 *
 * <p>Przeplot jest wymuszony deterministycznie: żądanie "atakującego" (wątek {@code attacker-*}) zostaje
 * zatrzymane w środku obsługi {@code /refresh}, dopóki drugie żądanie z tym samym tokenem nie skończy się
 * w całości. Dwa punkty zatrzymania pokrywają oba przeploty:
 * <ul>
 *   <li><b>zaraz po {@code RedisRefreshTokenStore.rotate}</b> — okno, w którym siedział błąd: gdy następcę
 *       zapisywał osobny krok po konsumpcji, drugie żądanie (REUSED → revokeAll) nie widziało go w ZSET
 *       i złodziej zachowywał sesję. Ten wariant czerwienieje po rozdzieleniu konsumpcji i zapisu;</li>
 *   <li><b>w {@code UserService.findUserByEmail}</b>, przed rotacją — atakujący przegrywa wyścig i to jego
 *       żądanie wykrywa reuse; sesja zwycięzcy też musi paść.</li>
 * </ul>
 * Niezmiennik: po obu żądaniach ŻADEN token wydany w tym wyścigu nie działa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenReuseRaceTest {

    private static final String JANE = "jane.smith@example.com";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired CacheManager cacheManager;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @MockitoSpyBean UserService userService;
    @MockitoSpyBean RedisRefreshTokenStore refreshTokenStore;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        resetSecurityState();
    }

    @AfterEach
    void tearDown() {
        resetSecurityState();
    }

    @Test
    @DisplayName("Atakujący zatrzymany PO rotacji, drugie żądanie wykrywa reuse -> następca atakującego unieważniony")
    void attackerParkedAfterRotate_successorRevoked() throws Exception {
        CountDownLatch attackerParked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            Object result = inv.callRealMethod();
            park(attackerParked, release);
            return result;
        }).when(refreshTokenStore).rotate(anyString(), anyString(), anyString(), any(Instant.class), any(Instant.class));

        assertNoSessionSurvives(attackerParked, release);
    }

    @Test
    @DisplayName("Atakujący zatrzymany PRZED rotacją, sam wykrywa reuse -> sesja zwycięzcy unieważniona")
    void attackerParkedBeforeRotate_winnerRevoked() throws Exception {
        CountDownLatch attackerParked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            park(attackerParked, release);
            return inv.callRealMethod();
        }).when(userService).findUserByEmail(any(Email.class));

        assertNoSessionSurvives(attackerParked, release);
    }

    private static void park(CountDownLatch attackerParked, CountDownLatch release) throws InterruptedException {
        if (Thread.currentThread().getName().startsWith("attacker-")) {
            attackerParked.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertNoSessionSurvives(CountDownLatch attackerParked, CountDownLatch release) throws Exception {
        String stolen = jwtService.generateToken(JANE, JwtTokenType.REFRESH, Set.of("ROLE_USER"));

        ExecutorService attacker = Executors.newSingleThreadExecutor(r -> new Thread(r, "attacker-1"));
        try {
            Future<MockHttpServletResponse> first = attacker.submit(() -> refresh(stolen));
            assertThat(attackerParked.await(10, TimeUnit.SECONDS)).as("żądanie atakującego zatrzymane w trakcie /refresh").isTrue();

            // drugie żądanie z tym samym tokenem przechodzi w całości, gdy pierwsze jest w połowie
            MockHttpServletResponse second = refresh(stolen);

            release.countDown();
            MockHttpServletResponse firstResponse = first.get(10, TimeUnit.SECONDS);

            // dokładnie jedno z żądań musiało trafić na ponowne użycie
            assertThat(new int[]{firstResponse.getStatus(), second.getStatus()}).contains(401);
            assertNoIssuedTokenWorks(firstResponse);
            assertNoIssuedTokenWorks(second);
        } finally {
            release.countDown();
            attacker.shutdownNow();
        }
    }

    private void assertNoIssuedTokenWorks(MockHttpServletResponse response) throws Exception {
        if (response.getStatus() != 200) {
            return;
        }
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        String refresh = Objects.requireNonNull(setCookie).split("refreshToken=")[1].split(";")[0];
        String access = response.getContentAsString().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        assertThat(refresh(refresh).getStatus())
                .as("refresh token wydany w wyścigu musi zostać unieważniony przez wykrycie reuse")
                .isEqualTo(401);
        assertThat(mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andReturn().getResponse().getStatus())
                .as("access token wydany w wyścigu musi zostać unieważniony przez wykrycie reuse")
                .isEqualTo(401);
    }

    private MockHttpServletResponse refresh(String refreshToken) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", refreshToken)))
                .andReturn().getResponse();
    }

    private void resetSecurityState() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        Cache cache = cacheManager.getCache("userAuthState");
        if (cache != null) {
            cache.clear();
        }
    }
}
