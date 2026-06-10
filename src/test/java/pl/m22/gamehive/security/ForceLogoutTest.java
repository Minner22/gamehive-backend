package pl.m22.gamehive.security;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.service.UserService;

import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForceLogoutTest {

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
        flushRedis();
    }

    @Test
    @DisplayName("force-logout -> stary access token jane -> 401 TOKEN_REVOKED")
    void oldAccessTokenRevokedAfterForceLogout() throws Exception {
        String janeToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        mockMvc.perform(post("/api/v1/admin/users/" + SeededUsers.JANE_ID + "/force-logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"));
    }

    @Test
    @DisplayName("force-logout -> refresh tokeny jane usunięte z Redis")
    void refreshTokensRemovedAfterForceLogout() throws Exception {
        jwtService.generateToken("jane.smith@example.com", JwtTokenType.REFRESH, null); // zapis w Redis
        assertEquals(Boolean.TRUE, redisTemplate.hasKey("user_refresh_tokens:jane.smith@example.com"));

        mockMvc.perform(post("/api/v1/admin/users/" + SeededUsers.JANE_ID + "/force-logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertNotEquals(Boolean.TRUE, redisTemplate.hasKey("user_refresh_tokens:jane.smith@example.com"));
    }

    // W przeciwieństwie do dezaktywacji (enabled=false -> login blokowany), force-logout nie rusza konta.
    // Pełnego /login jako jane nie testujemy: hash jane w data.sql to nieznane hasło fixture'owe.
    // Asercja enabled=true pokrywa kryterium "konto pozostaje aktywne, login nie jest zablokowany".
    @Test
    @DisplayName("force-logout -> konto pozostaje aktywne (enabled=true), login nie jest zablokowany")
    void accountStaysEnabledAfterForceLogout() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + SeededUsers.JANE_ID + "/force-logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertTrue(userService.findUserById(SeededUsers.JANE_ID).isEnabled());
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