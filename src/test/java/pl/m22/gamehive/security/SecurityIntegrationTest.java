package pl.m22.gamehive.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    @MockitoBean JavaMailSender mailSender;

    @Test
    @DisplayName("GET /api/v1/users/me without token -> 403")
    void users_unauthenticated_403() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/me with invalid Authorization header (no Bearer prefix) -> 403")
    void users_invalid_header_no_bearer_403() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "InvalidToken"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/me with empty Bearer token -> 403")
    void users_empty_bearer_403() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer "))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/me with blacklisted token -> 403")
    void users_blacklisted_token_403() throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        tokenBlacklistService.blacklistToken(token);
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/me with valid token -> 200")
    void users_me_authenticated_200() throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/users/me with ACTIVATION token type -> 403")
    void users_me_wrong_token_type_403() throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACTIVATION, null);
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }
}
