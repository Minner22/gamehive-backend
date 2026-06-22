package pl.m22.gamehive.user.controller;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.repository.UserAuditLogRepository;
import pl.m22.gamehive.user.repository.UserRepository;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeleteOwnAccountTest {

    private static final String EMAIL = "ctrl_deleteme@test.com";
    private static final String PASSWORD = "deletePass1";
    private static final String JOHN_EMAIL = "john.doe@example.com";
    private static final String SEED_PASSWORD = "password123";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired UserAuditLogRepository auditLogRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired CacheManager cacheManager;
    @MockitoBean JavaMailSender mailSender;

    private String johnToken; // zalogowany seedowy admin (jedyny ADMIN)

    @BeforeEach
    void setUp() {
        flushRedis();
        clearCache();
        johnToken = jwtService.generateToken(JOHN_EMAIL, JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
    }

    @AfterEach
    void tearDown() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
        auditLogRepository.deleteAll();
        flushRedis();
        clearCache();
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me poprawne hasło -> 204; konto usunięte, audyt DELETE, ciasteczko wyczyszczone, stary token nieważny")
    void deleteOwnAccount_happyPath_204() throws Exception {
        String accessToken = registerActivateLogin();
        UUID targetId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        MvcResult result = mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"password":"%s"}
                                  """.formatted(PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();

        // ciasteczko refresh wyczyszczone
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refreshToken=").contains("Max-Age=0");

        // konto fizycznie usunięte
        assertThat(userRepository.findByEmail(EMAIL)).isEmpty();

        // dokładnie jeden wpis audytu DELETE, actor == target (self-service)
        var entries = auditLogRepository.findByTargetId(targetId);
        assertThat(entries).hasSize(1);
        var entry = entries.getFirst();
        assertThat(entry.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(entry.getActor()).isEqualTo(EMAIL);
        assertThat(entry.getTargetEmail()).isEqualTo(EMAIL);

        // stary access token przestaje działać (session epoch + brak konta)
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me błędne hasło -> 401 INVALID_PASSWORD, konto nietknięte")
    void deleteOwnAccount_wrongPassword_401() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"password":"definitely-wrong"}
                                  """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PASSWORD"));

        assertThat(userRepository.findByEmail(JOHN_EMAIL)).isPresent();
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me ostatni admin -> 409 CANNOT_REMOVE_LAST_ADMIN, konto nietknięte")
    void deleteOwnAccount_lastAdmin_409() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"password":"%s"}
                                  """.formatted(SEED_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_REMOVE_LAST_ADMIN"));

        assertThat(userRepository.findByEmail(JOHN_EMAIL)).isPresent();
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me puste hasło -> 400")
    void deleteOwnAccount_blankPassword_400() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + johnToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"password":""}
                                  """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me bez tokena -> 401")
    void deleteOwnAccount_unauthenticated_401() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"password":"%s"}
                                  """.formatted(SEED_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // rejestracja (enabled=false) -> aktywacja -> logowanie; zwraca świeży access token
    private String registerActivateLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"username":"ctrl_deleteme","email":"%s","password":"%s"}
                                  """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk());

        String activation = jwtService.generateToken(EMAIL, JwtTokenType.ACTIVATION, null);
        mockMvc.perform(get("/api/v1/auth/activate").param("token", activation))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"email":"%s","password":"%s"}
                                  """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private void clearCache() {
        Cache c = cacheManager.getCache("userAuthState");
        if (c != null) c.clear();
    }

    private void flushRedis() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }
}