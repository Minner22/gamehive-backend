package pl.m22.gamehive.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.TokenBlacklistService;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired TokenBlacklistService tokenBlacklistService;

    @Test
    @DisplayName("GET /api/v1/users without token -> 403")
    void users_unauthenticated_403() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users with invalid Authorization header (no Bearer prefix) -> 403")
    void users_invalid_header_no_bearer_403() throws Exception {
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "InvalidToken"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users with empty Bearer token -> 403")
    void users_empty_bearer_403() throws Exception {
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer "))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users with blacklisted ADMIN token -> 403")
    void users_blacklisted_token_403() throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        tokenBlacklistService.blacklistAccessToken(token);
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users with token USER -> 403")
    void users_user_forbidden_403() throws Exception {
        String token = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users with token ADMIN -> 200")
    void users_admin_ok_200() throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN","ROLE_USER"));
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }
}
