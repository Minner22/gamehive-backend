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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.repository.UserAuditLogRepository;
import pl.m22.gamehive.user.repository.UserRepository;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH-168: tryby pracy przy niedostępnym Redisie, sprawdzane przez PRAWDZIWE usługi (awaria symulowana na
 * szpiegu {@code RedisTemplate}: {@code hasKey} i {@code opsForValue()} rzucają {@link QueryTimeoutException}).
 * <ul>
 *   <li>{@code /password-reset/confirm} — fail-closed: 503 PRZED zmianą hasła, brak audytu;</li>
 *   <li>{@code /activate} — świadomie fail-open (link trafił do właściciela adresu, nie daje dostępu do cudzego konta);</li>
 *   <li>{@code JwtAuthFilter} — fail-open (dostępność dla zalogowanych, GH-165).</li>
 * </ul>
 * Bez {@code @Transactional}: audyt to AFTER_COMMIT, a test sprawdza, że go NIE ma.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetRedisUnavailableTest {

    private static final String RESET_EMAIL = "pwr168@test.com";
    private static final String ACTIVATE_EMAIL = "act168@test.com";
    private static final String OLD_PASSWORD = "oldPassword1";
    private static final String NEW_PASSWORD = "newPassword1";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired UserAuditLogRepository auditLogRepository;
    @Autowired CacheManager cacheManager;
    @MockitoSpyBean RedisTemplate<String, String> redisTemplate;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        resetSecurityState();
    }

    @AfterEach
    void cleanup() {
        Mockito.reset(redisTemplate);   // Redis "wraca" przed sprzątaniem
        userRepository.findByEmail(RESET_EMAIL).ifPresent(u -> {
            auditLogRepository.deleteAll(auditLogRepository.findByTargetId(u.getId()));
            userRepository.delete(u);
        });
        userRepository.findByEmail(ACTIVATE_EMAIL).ifPresent(userRepository::delete);
        resetSecurityState();
    }

    @Test
    @DisplayName("confirm przy niedostępnym Redis -> 503 REDIS_UNAVAILABLE, hasło NIEZMIENIONE, brak audytu PASSWORD_CHANGE")
    void confirm_redisDown_failsClosedBeforePasswordChange() throws Exception {
        UUID userId = registerAndActivate("pwr168", RESET_EMAIL);
        String link = jwtService.generateToken(RESET_EMAIL, JwtTokenType.PASSWORD_RESET, null);

        redisDown();
        confirm(link)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("REDIS_UNAVAILABLE"));

        Mockito.reset(redisTemplate);   // Redis wraca — teraz sprawdzamy skutki
        assertPasswordUnchanged();
        assertThat(auditLogRepository.findByTargetId(userId))
                .noneMatch(e -> e.getAction() == AuditAction.PASSWORD_CHANGE);

        // ten sam link po powrocie Redisa nadal działa — odmowa niczego nie "spaliła"
        confirm(link).andExpect(status().isOk());
    }

    @Test
    @DisplayName("confirm, gdy blacklista działa, a pada dopiero odczyt epoki resetu -> też 503, hasło niezmienione")
    void confirm_epochReadDown_failsClosed() throws Exception {
        registerAndActivate("pwr168", RESET_EMAIL);
        String link = jwtService.generateToken(RESET_EMAIL, JwtTokenType.PASSWORD_RESET, null);

        valueOperationsDown();   // hasKey (blacklista) działa normalnie
        confirm(link)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("REDIS_UNAVAILABLE"));

        Mockito.reset(redisTemplate);
        assertPasswordUnchanged();
    }

    @Test
    @DisplayName("confirm, gdy pada tylko blacklista (epoki działają) -> 503: blacklista też musi być strict")
    void confirm_blacklistOnlyDown_failsClosed() throws Exception {
        registerAndActivate("pwr168", RESET_EMAIL);
        String link = jwtService.generateToken(RESET_EMAIL, JwtTokenType.PASSWORD_RESET, null);

        // bez tego przypadku powrót do fail-open isBlacklisted w confirm przeszedłby niezauważony,
        // bo w pełnej awarii i tak zatrzymałby go strict odczyt epoki
        doThrow(new QueryTimeoutException("Redis command timed out")).when(redisTemplate).hasKey(anyString());
        confirm(link)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("REDIS_UNAVAILABLE"));

        Mockito.reset(redisTemplate);
        assertPasswordUnchanged();
    }

    @Test
    @DisplayName("/activate przy niedostępnym Redis -> 200 (świadomie fail-open, GH-168)")
    void activate_redisDown_staysFailOpen() throws Exception {
        register("act168", ACTIVATE_EMAIL);
        String activationToken = jwtService.generateToken(ACTIVATE_EMAIL, JwtTokenType.ACTIVATION, null);

        redisDown();
        mockMvc.perform(get("/api/v1/auth/activate").param("token", activationToken))
                .andExpect(status().isOk());

        Mockito.reset(redisTemplate);
        assertThat(userRepository.findByEmail(ACTIVATE_EMAIL)).hasValueSatisfying(u -> assertThat(u.isEnabled()).isTrue());
    }

    @Test
    @DisplayName("Bearer przy niedostępnym Redis -> 200: JwtAuthFilter pozostaje fail-open (GH-165)")
    void bearer_redisDown_staysFailOpen() throws Exception {
        String accessToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        redisDown();
        mockMvc.perform(get("/api/v1/collection/games/ids")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    // ---------- pomocnicze ----------

    /** Cały Redis "zawieszony": blacklista (hasKey) i wszystkie operacje na wartościach (epoki, zapisy). */
    private void redisDown() {
        doThrow(new QueryTimeoutException("Redis command timed out")).when(redisTemplate).hasKey(anyString());
        valueOperationsDown();
    }

    @SuppressWarnings("unchecked")
    private void valueOperationsDown() {
        ValueOperations<String, String> failing = Mockito.mock(ValueOperations.class, _ -> {
            throw new QueryTimeoutException("Redis command timed out");
        });
        doReturn(failing).when(redisTemplate).opsForValue();
    }

    private UUID registerAndActivate(String username, String email) throws Exception {
        register(username, email);
        String activationToken = jwtService.generateToken(email, JwtTokenType.ACTIVATION, null);
        mockMvc.perform(get("/api/v1/auth/activate").param("token", activationToken))
                .andExpect(status().isOk());
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private void register(String username, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"%s"}
                                """.formatted(username, email, OLD_PASSWORD)))
                .andExpect(status().isOk());
    }

    private ResultActions confirm(String link) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","newPassword":"%s"}
                        """.formatted(link, NEW_PASSWORD)));
    }

    private void assertPasswordUnchanged() throws Exception {
        login(OLD_PASSWORD).andExpect(status().isOk());
        login(NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(RESET_EMAIL, password)));
    }

    private void resetSecurityState() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        Cache cache = cacheManager.getCache("userAuthState");
        if (cache != null) {
            cache.clear();
        }
    }
}
