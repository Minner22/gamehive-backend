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
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.service.UserService;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenRevocationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserService userService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired CacheManager cacheManager;
    @MockitoBean JavaMailSender mailSender;

    private String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        flushRedis();
        clearCache();
    }

    @AfterEach
    void tearDown() {
        if (!userService.findUserById(SeededUsers.JANE_ID).isEnabled()) { // reaktywuj tylko jeśli zdezaktywowana
            userService.activateUser(SeededUsers.JANE_ID, new Email("john.doe@example.com"));
        }
        flushRedis();
    }

    @Test
    @DisplayName("po dezaktywacji refresh token -> 401")
    void refreshTokenRejectedAfterDeactivation() throws Exception {
        // refresh token zapisany w Redis bezpośrednio (bez zależności od hasła jane w fixtures)
        String refreshToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.REFRESH, null);

        mockMvc.perform(patch("/api/v1/admin/users/" + SeededUsers.JANE_ID + "/deactivate")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/refresh")
                            .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deleteUser() czyści refresh tokeny z Redis (AFTER_COMMIT)")
    void deleteUserRevokesRefreshTokens() throws Exception {
        String email = "throwaway_revoke@test.com";
        // realnie zapisany user (register -> enabled=false, ale deleteUser nie sprawdza enabled)
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                    {"username":"throwaway_rev","email":"%s","password":"password123"}
                                    """.formatted(email)))
                .andExpect(status().isOk());
        UUID userId = userService.findUserByEmail(new Email(email)).getId();

        jwtService.generateToken(email, JwtTokenType.REFRESH, null); // zapis w Redis
        assertEquals(Boolean.TRUE, redisTemplate.hasKey("user_refresh_tokens:" + email));

        // aktywna ścieżka: @Transactional -> commit -> UserDeletedEvent -> AFTER_COMMIT -> rewokacja
        userService.deleteUser(userId, new Email("john.doe@example.com"));

        assertNotEquals(Boolean.TRUE, redisTemplate.hasKey("user_refresh_tokens:" + email));
    }

    private void flushRedis() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    private void clearCache() {
        Cache c = cacheManager.getCache("userAuthState");

        if (c != null) {
            c.clear();
        }
    }
}