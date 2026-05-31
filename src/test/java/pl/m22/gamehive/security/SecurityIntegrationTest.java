package pl.m22.gamehive.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.TokenBlacklistService;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.user.service.UserService;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TokenBlacklistService tokenBlacklistService;
    @Autowired UserService userService;
    @Autowired CacheManager cacheManager;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void clearAuthStateCache() {
        Cache c = cacheManager.getCache("userAuthState");

        if (c != null) {
            c.clear();
        }
    }

    @Test
    @DisplayName("GET /api/v1/users/me without token -> 401")
    void users_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/me with invalid Authorization header (no Bearer prefix) -> 401")
    void users_invalid_header_no_bearer_401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "InvalidToken"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/me with empty Bearer token -> 401")
    void users_empty_bearer_401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer "))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/me with blacklisted token -> 401")
    void users_blacklisted_token_401() throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        tokenBlacklistService.blacklistToken(token);
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users/me with valid token -> 200")
    void users_me_authenticated_200() throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/users/me with ACTIVATION token type -> 401")
    void users_me_wrong_token_type_401() throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACTIVATION, null);
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    @DisplayName("GET /api/v1/users/me with valid token but user is disabled -> 401")
    void users_me_disabled_user_401() throws Exception {
        String token = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        userService.deactivateUser(2L, new Email("john.doe@example.com"));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
    }
}
