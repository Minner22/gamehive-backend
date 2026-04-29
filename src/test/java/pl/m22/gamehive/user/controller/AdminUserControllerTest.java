package pl.m22.gamehive.user.controller;

import org.junit.jupiter.api.BeforeEach;
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

import java.util.Set;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @MockitoBean JavaMailSender mailSender;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        userToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
    }

    // --- getAllUsers ---

    @Test
    @DisplayName("GET /api/v1/admin/users/ jako ADMIN -> 200 + Page z userami")
    void getAllUsers_asAdmin_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.content[0].username").exists())
                .andExpect(jsonPath("$.content[0].email").exists())
                .andExpect(jsonPath("$.content[0].roles").isArray())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/ jako USER -> 403")
    void getAllUsers_asUser_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/ bez tokena -> 401")
    void getAllUsers_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/"))
                .andExpect(status().isUnauthorized());
    }

    // --- getUserById ---

    @Test
    @DisplayName("GET /api/v1/admin/users/{id} istniejący -> 200")
    void getUserById_found_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/{id} nieistniejący -> 404")
    void getUserById_notFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // --- getUserByUsername ---

    @Test
    @DisplayName("GET /api/v1/admin/users/by-username/{username} istniejący -> 200")
    void getUserByUsername_found_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/by-username/jane_smith")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jane_smith"))
                .andExpect(jsonPath("$.email").value("jane.smith@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/by-username/{username} nieistniejący -> 404")
    void getUserByUsername_notFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/by-username/ghost_user")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // --- getUserByEmail ---

    @Test
    @DisplayName("GET /api/v1/admin/users/by-email/{email} istniejący -> 200")
    void getUserByEmail_found_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/by-email/jane.smith@example.com")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane.smith@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/by-email/{email} nieistniejący -> 404")
    void getUserByEmail_notFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/by-email/nobody@test.com")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}