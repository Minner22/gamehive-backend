package pl.m22.gamehive.user.controller;

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
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;

import java.util.Objects;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired CacheManager cacheManager;
    @MockitoBean JavaMailSender mailSender;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        userToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();

        clearCache();
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

    // --- PUT /{id}/roles ---

    @Test
    @Transactional
    @DisplayName("PUT /api/v1/admin/users/{id}/roles jako ADMIN -> 200 + zaktualizowane role")
    void updateUserRoles_asAdmin_200() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/2/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_MODERATOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_MODERATOR")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/users/{id}/roles jako USER -> 403")
    void updateUserRoles_asUser_403() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/2/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_MODERATOR\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/admin/users/{id}/roles bez tokena -> 401")
    void updateUserRoles_unauthenticated_401() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/2/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_MODERATOR\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/admin/users/{id}/roles nieistniejący user -> 404")
    void updateUserRoles_userNotFound_404() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/999/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/admin/users/{id}/roles nieistniejąca rola -> 404")
    void updateUserRoles_roleNotFound_404() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/2/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_GHOST\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/admin/users/{id}/roles self -> 403 (CANNOT_MODIFY_OWN_ACCOUNT)")
    void updateUserRoles_self_403() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/1/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/admin/users/{id}/roles pusty zbiór ról -> 400 (validation)")
    void updateUserRoles_emptyRoles_400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/2/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // --- PATCH /{id}/deactivate ---

    @Test
    @Transactional
    @DisplayName("PATCH /api/v1/admin/users/{id}/deactivate jako ADMIN -> 200 + enabled=false")
    void deactivateUser_asAdmin_200() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/2/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/deactivate jako USER -> 403")
    void deactivateUser_asUser_403() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/2/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/deactivate nieistniejący user -> 404")
    void deactivateUser_userNotFound_404() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/999/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/deactivate self -> 403 (CANNOT_MODIFY_OWN_ACCOUNT)")
    void deactivateUser_self_403() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // --- PATCH /{id}/activate ---

    @Test
    @Transactional
    @DisplayName("PATCH /api/v1/admin/users/{id}/activate jako ADMIN -> 200 + enabled=true")
    void activateUser_asAdmin_200() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/2/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/users/2/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/activate jako USER -> 403")
    void activateUser_asUser_403() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/2/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/activate bez tokena -> 401")
    void activateUser_unauthenticated_401() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/2/activate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/activate nieistniejący user -> 404")
    void activateUser_userNotFound_404() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/999/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /{id} ---

    @Test
    @Transactional
    @DisplayName("DELETE /api/v1/admin/users/{id} jako ADMIN -> 204")
    void deleteUser_asAdmin_204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/users/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/users/{id} jako USER -> 403")
    void deleteUser_asUser_403() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/users/{id} bez tokena -> 401")
    void deleteUser_unauthenticated_401() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/users/{id} nieistniejący user -> 404")
    void deleteUser_userNotFound_404() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/users/{id} self -> 403 (CANNOT_MODIFY_OWN_ACCOUNT)")
    void deleteUser_self_403() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    private void clearCache() {
        Cache c = cacheManager.getCache("userAuthState");

        if (c != null) {
            c.clear();
        }
    }
}