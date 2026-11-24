package pl.m22.gamehive.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
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
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.HashedPassword;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH-172: {@code /refresh} odmawia wyłączonemu kontu (401 {@code ACCOUNT_DISABLED}) i sprząta wszystkie jego refresh
 * tokeny, zanim cokolwiek zrotuje. Sytuację "refresh przeżył deaktywację" (best-effort rewokacja przy padniętym
 * Redisie) symulujemy, wyłączając konto bezpośrednio w repozytorium — z pominięciem serwisu i zdarzenia, więc
 * tokeny w Redisie zostają.
 *
 * <p>Własne konto testowe zamiast fixture'ów z {@code data.sql}: test zmienia stan konta. Zestaw nadpisań beanów
 * (szpieg {@code RedisTemplate} + mock {@code JavaMailSender}) jak w {@code RefreshTokenRotationTest} — wspólny kontekst.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshDisabledAccountTest {

    private static final String EMAIL = "refresh.disabled@test.com";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired CacheManager cacheManager;
    @MockitoSpyBean RedisTemplate<String, String> redisTemplate;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        resetSecurityState();
        AppUser user = AppUser.register(new Username("refresh_disabled"), new Email(EMAIL), HashedPassword.fromHash("{noop}secret123"));
        user.activate();
        user.assignRole(userRoleRepository.findByName("ROLE_USER").orElseThrow());
        userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(redisTemplate);
        userRepository.findByEmail(new Email(EMAIL)).ifPresent(userRepository::delete);
        resetSecurityState();
    }

    @Test
    @DisplayName("Konto aktywne -> /refresh 200 (regresja)")
    void enabledAccount_refreshes() throws Exception {
        String refreshToken = issueRefresh();

        refresh(refreshToken).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Wyłączone konto -> 401 ACCOUNT_DISABLED, bez nowego cookie, bez rotacji, WSZYSTKIE refreshe konta usunięte")
    void disabledAccount_rejectedAndAllRefreshTokensRevoked() throws Exception {
        String presented = issueRefresh();
        String otherDevice = issueRefresh();
        disableBypassingService();

        MockHttpServletResponse response = refresh(presented)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"))
                .andReturn().getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(redisTemplate.hasKey("refresh_rotated:" + jwtService.extractJtiFromToken(presented)))
                .as("sprawdzenie przed rotacją — token nie został skonsumowany").isFalse();
        assertThat(jtiExists(presented)).isFalse();
        assertThat(jtiExists(otherDevice)).isFalse();
        assertThat(redisTemplate.hasKey("user_refresh_tokens:" + EMAIL)).isFalse();
    }

    @Test
    @DisplayName("Po reaktywacji ocalały refresh NIE ożywa -> 401 JWT_INVALID_JTI (reaktywacja wymaga ponownego logowania)")
    void reactivatedAccount_survivingRefreshDoesNotResurrect() throws Exception {
        String survivor = issueRefresh();
        disableBypassingService();
        refresh(survivor).andExpect(status().isUnauthorized());

        AppUser user = userRepository.findByEmail(new Email(EMAIL)).orElseThrow();
        user.activate();
        userRepository.save(user);

        refresh(survivor)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("JWT_INVALID_JTI"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")
    @DisplayName("Redis pada przy sprzątaniu -> nadal 401 ACCOUNT_DISABLED (best-effort), nie 503")
    void disabledAccount_revokeFails_stillAccountDisabled(RuntimeException failure) throws Exception {
        String refreshToken = issueRefresh();
        disableBypassingService();
        // rewokacja = skrypt REVOKE_ALL (jedyne execute(script) na tej ścieżce — rotacja nie zostaje osiągnięta)
        doThrow(failure).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
    }

    // ---------- pomocnicze ----------

    private String issueRefresh() {
        return jwtService.generateToken(EMAIL, JwtTokenType.REFRESH, Set.of("ROLE_USER"));
    }

    private void disableBypassingService() {
        AppUser user = userRepository.findByEmail(new Email(EMAIL)).orElseThrow();
        user.deactivate();
        userRepository.save(user);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", refreshToken)));
    }

    private boolean jtiExists(String refreshToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("refresh_token:" + jwtService.extractJtiFromToken(refreshToken)));
    }

    private void resetSecurityState() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        Cache cache = cacheManager.getCache("userAuthState");
        if (cache != null) {
            cache.clear();
        }
    }
}
