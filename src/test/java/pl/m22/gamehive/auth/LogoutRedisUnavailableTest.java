package pl.m22.gamehive.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.TokenBlacklistService;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH-148: logout albo w pełni się udaje (200), albo jawnie się nie udaje (503 {@code REDIS_UNAVAILABLE}),
 * a ciasteczko {@code refreshToken} jest czyszczone ZAWSZE (po poprawnej walidacji tokena). Oba kroki Redis
 * (blacklista access tokena, rewokacja refreshy) są próbowane niezależnie — częściowa awaria robi tyle, ile się da.
 *
 * <p>Cookie trafia do odpowiedzi 503, bo kontroler ustawia je na {@code HttpServletResponse} PRZED krokami Redis,
 * a {@code DispatcherServlet.processHandlerException} robi {@code resetBuffer()}, nie {@code reset()} — nagłówki
 * przeżywają obsługę wyjątku. Ten test to pinuje (MockMvc przechodzi przez prawdziwy DispatcherServlet).
 *
 * <p>Awaria symulowana na szpiegu {@code RedisTemplate}: {@code opsForValue()} (blacklista) i {@code opsForZSet()}
 * (rewokacja) zwracają mocki rzucające {@link QueryTimeoutException}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogoutRedisUnavailableTest {

    private static final String JANE = "jane.smith@example.com";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TokenBlacklistService tokenBlacklistService;
    @Autowired CacheManager cacheManager;
    @MockitoSpyBean RedisTemplate<String, String> redisTemplate;
    @MockitoBean JavaMailSender mailSender;

    private String accessToken;
    private String refreshJti;

    @BeforeEach
    void setUp() {
        resetSecurityState();
        accessToken = jwtService.generateToken(JANE, JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        // REFRESH zapisuje swój JTI w Redis (refresh_token:{jti} + user_refresh_tokens:{email})
        String refreshToken = jwtService.generateToken(JANE, JwtTokenType.REFRESH, Set.of("ROLE_USER"));
        refreshJti = jwtService.extractJtiFromToken(refreshToken);
        assertThat(refreshExists()).isTrue();
    }

    @AfterEach
    void cleanup() {
        Mockito.reset(redisTemplate);
        resetSecurityState();   // epoki/blacklisty Jane nie mogą przeciec do innych klas
    }

    @Test
    @DisplayName("Redis dostępny -> 200, cookie wyczyszczone dokładnie raz, access token na blackliście, refreshe usunięte")
    void logout_redisUp_fullySucceeds() throws Exception {
        MockHttpServletResponse response = logout()
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertClearedCookie(response);
        assertThat(tokenBlacklistService.isBlacklisted(jwtService.extractJtiFromToken(accessToken))).isTrue();
        assertThat(refreshExists()).isFalse();
    }

    @Test
    @DisplayName("Redis niedostępny -> 503 REDIS_UNAVAILABLE, ale Set-Cookie refreshToken=; Max-Age=0 jest w odpowiedzi błędu")
    void logout_redisDown_503WithClearedCookie() throws Exception {
        valueOpsDown();
        zSetOpsDown();

        MockHttpServletResponse response = logout()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("REDIS_UNAVAILABLE"))
                .andReturn().getResponse();

        assertClearedCookie(response);
    }

    @Test
    @DisplayName("Pada tylko blacklista -> 503, ale rewokacja refreshy i tak wykonana (oba kroki próbowane)")
    void logout_blacklistDown_revokeStillApplied() throws Exception {
        valueOpsDown();

        assertClearedCookie(logout()
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse());

        Mockito.reset(redisTemplate);
        assertThat(refreshExists()).isFalse();
    }

    @Test
    @DisplayName("Pada tylko rewokacja -> 503, ale access token i tak na blackliście")
    void logout_revokeDown_blacklistStillApplied() throws Exception {
        zSetOpsDown();

        assertClearedCookie(logout()
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse());

        Mockito.reset(redisTemplate);
        assertThat(tokenBlacklistService.isBlacklisted(jwtService.extractJtiFromToken(accessToken))).isTrue();
    }

    @Test
    @DisplayName("Nieważny access token -> 401, cookie NIE jest czyszczone (świadoma decyzja GH-148: najpierw walidacja)")
    void logout_invalidToken_401WithoutCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt"))
                .andExpect(status().is4xxClientError())
                .andReturn().getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(refreshExists()).isTrue();
    }

    // ---------- pomocnicze ----------

    private ResultActions logout() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private static void assertClearedCookie(MockHttpServletResponse response) {
        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).as("dokładnie jeden nagłówek Set-Cookie").hasSize(1);
        assertThat(cookies.getFirst())
                .startsWith("refreshToken=;")
                .contains("Max-Age=0")
                .contains("Path=/api/v1/auth/refresh")
                .contains("HttpOnly");
    }

    private boolean refreshExists() {
        return Boolean.TRUE.equals(redisTemplate.hasKey("refresh_token:" + refreshJti));
    }

    @SuppressWarnings("unchecked")
    private void valueOpsDown() {
        ValueOperations<String, String> failing = Mockito.mock(ValueOperations.class, _ -> {
            throw new QueryTimeoutException("Redis command timed out");
        });
        doReturn(failing).when(redisTemplate).opsForValue();
    }

    @SuppressWarnings("unchecked")
    private void zSetOpsDown() {
        ZSetOperations<String, String> failing = Mockito.mock(ZSetOperations.class, _ -> {
            throw new QueryTimeoutException("Redis command timed out");
        });
        doReturn(failing).when(redisTemplate).opsForZSet();
    }

    private void resetSecurityState() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        Cache cache = cacheManager.getCache("userAuthState");
        if (cache != null) {
            cache.clear();
        }
    }
}
