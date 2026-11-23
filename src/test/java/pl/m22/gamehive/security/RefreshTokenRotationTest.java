package pl.m22.gamehive.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
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
import pl.m22.gamehive.auth.jwt.service.JwtServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH-146: każdy udany {@code /refresh} rotuje refresh token (stary przestaje działać), a ponowne użycie już
 * zrotowanego tokena to podejrzenie kradzieży → 401 {@code REFRESH_TOKEN_REUSED} i unieważnienie WSZYSTKICH sesji
 * użytkownika (refreshe + epoka sesji, czyli też access tokeny). Token wypchnięty limitem albo po logoucie daje
 * zwykłe {@code JWT_INVALID_JTI} — bez globalnego wylogowania.
 *
 * <p>Szpieg {@code RedisTemplate} służy tylko testom awarii (skrypt Lua rzuca); zestaw nadpisań beanów jest taki sam
 * jak w {@code LogoutRedisUnavailableTest}, więc kontekst Springa jest współdzielony.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenRotationTest {

    private static final String JANE = "jane.smith@example.com";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired CacheManager cacheManager;
    @MockitoSpyBean RedisTemplate<String, String> redisTemplate;
    @MockitoBean JavaMailSender mailSender;

    private final Logger jwtLogger = (Logger) LoggerFactory.getLogger(JwtServiceImpl.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        resetSecurityState();
        appender.start();
        jwtLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        jwtLogger.detachAppender(appender);
        Mockito.reset(redisTemplate);
        resetSecurityState();   // epoka/refreshe Jane nie mogą przeciec do innych klas
    }

    // ---------- AC1: rotacja ----------

    @Test
    @DisplayName("AC1: login -> /refresh 200 z NOWYM cookie (te same atrybuty), stary refresh przestaje działać")
    void refresh_rotatesToken() throws Exception {
        String original = refreshCookieOf(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"john.doe@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk()));

        ResultActions refreshed = refresh(original)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
        String setCookie = refreshed.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains("Path=/api/v1/auth/refresh")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Max-Age=604800");
        String rotated = refreshCookieOf(refreshed);

        assertThat(rotated).isNotEqualTo(original);
        assertThat(jtiExists(original)).as("stary JTI usunięty z Redisa przy rotacji").isFalse();
        assertThat(jtiExists(rotated)).isTrue();
        // stary refresh nie działa (a że to ponowne użycie zrotowanego — AC2 — kończy się REFRESH_TOKEN_REUSED)
        refresh(original)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REFRESH_TOKEN_REUSED"));
    }

    // ---------- AC2: ponowne użycie = kradzież ----------

    @Test
    @DisplayName("AC2: ponowne użycie zrotowanego tokena -> 401 REFRESH_TOKEN_REUSED, nowy refresh i access token unieważnione")
    void reuse_revokesAllSessions() throws Exception {
        String stolen = jwtService.generateToken(JANE, JwtTokenType.REFRESH, Set.of("ROLE_USER"));
        String otherDevice = jwtService.generateToken(JANE, JwtTokenType.REFRESH, Set.of("ROLE_USER"));
        String rotated = refreshCookieOf(refresh(stolen).andExpect(status().isOk()));

        // dotychczasowy access token Jane działa i "rozgrzewa" cache userAuthState (stan BEZ epoki)
        String access = jwtService.generateToken(JANE, JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk());
        Thread.sleep(2);   // epoka w ms vs iat ucięty do sekund: epoka musi być > iat

        refresh(stolen)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REFRESH_TOKEN_REUSED"));

        // wszystkie refreshe Jane (z rotacji i z innego urządzenia) unieważnione
        refresh(rotated)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("JWT_INVALID_JTI"));
        assertThat(jtiExists(otherDevice)).isFalse();
        // access token odrzucony mimo rozgrzanego cache -> eviction po zapisie epoki zadziałała
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"));
    }

    @Test
    @DisplayName("AC2: incydent logowany na WARN z zamaskowanym e-mailem (bez pełnego adresu)")
    void reuse_loggedAtWarnWithObfuscatedEmail() throws Exception {
        String token = jwtService.generateToken(JANE, JwtTokenType.REFRESH, Set.of("ROLE_USER"));
        refresh(token).andExpect(status().isOk());

        refresh(token).andExpect(status().isUnauthorized());

        List<ILoggingEvent> reuseLogs = new ArrayList<>(appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("reuse detected"))
                .toList());
        assertThat(reuseLogs).hasSize(1);
        assertThat(reuseLogs.getFirst().getLevel().toString()).isEqualTo("WARN");
        assertThat(reuseLogs.getFirst().getFormattedMessage())
                .doesNotContain(JANE)
                .contains(jwtService.extractJtiFromToken(token));
    }

    // ---------- AC3: wypchnięcie limitem i logout to NIE kradzież ----------

    @Test
    @DisplayName("AC3: refresh wypchnięty limitem 5 -> 401 JWT_INVALID_JTI, BEZ globalnego wylogowania")
    void evictedByLimit_plainUnauthorized() throws Exception {
        String evicted = jwtService.generateToken(JANE, JwtTokenType.REFRESH, Set.of("ROLE_USER"));
        List<String> newer = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            newer.add(jwtService.generateToken(JANE, JwtTokenType.REFRESH, Set.of("ROLE_USER")));
        }
        String access = jwtService.generateToken(JANE, JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        refresh(evicted)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("JWT_INVALID_JTI"));

        assertThat(redisTemplate.hasKey("token_invalid_after:" + JANE)).isFalse();
        assertThat(newer).allSatisfy(t -> assertThat(jtiExists(t)).isTrue());
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("refresh po logoucie -> 401 JWT_INVALID_JTI, bez znacznika rotacji i bez epoki sesji")
    void afterLogout_plainUnauthorized() throws Exception {
        String refreshToken = jwtService.generateToken(JANE, JwtTokenType.REFRESH, Set.of("ROLE_USER"));
        jwtService.revokeUsersTokens(JANE);

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("JWT_INVALID_JTI"));
        assertThat(redisTemplate.hasKey("token_invalid_after:" + JANE)).isFalse();
    }

    // ---------- awaria Redisa ----------

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")
    @DisplayName("Redis niedostępny przy rotacji -> 503 REDIS_UNAVAILABLE (nie 401, nie 500), token nieskonsumowany")
    void redisDown_503(RuntimeException failure) throws Exception {
        String refreshToken = jwtService.generateToken(JANE, JwtTokenType.REFRESH, Set.of("ROLE_USER"));
        doThrow(failure).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(), any(), any());

        refresh(refreshToken)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("REDIS_UNAVAILABLE"));

        Mockito.reset(redisTemplate);
        refresh(refreshToken).andExpect(status().isOk());   // Redis wrócił -> ten sam token nadal działa
    }

    // ---------- pomocnicze ----------

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", refreshToken)));
    }

    private static String refreshCookieOf(ResultActions result) {
        String setCookie = result.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("refreshToken=");
        return setCookie.split("refreshToken=")[1].split(";")[0];
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
