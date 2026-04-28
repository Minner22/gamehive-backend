package pl.m22.gamehive.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @MockitoBean JavaMailSender mailSender;

    private String userToken;

    @BeforeEach
    void setUp() {
        userToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
    }

    // --- GET /me ---

    @Test
    @DisplayName("GET /api/v1/users/me zalogowany -> 200 + dane usera")
    void getMe_authenticated_200() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john_doe"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/users/me bez tokena -> 403")
    void getMe_unauthenticated_403() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden());
    }

    // --- PATCH /me/profile ---

    @Test
    @Transactional
    @DisplayName("PATCH /api/v1/users/me/profile poprawne dane -> 200 + zaktualizowany profil")
    void updateProfile_validData_200() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Johnny","lastName":"Updated","phoneNumber":"+48111222333","address":"Nowy adres"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Johnny"))
                .andExpect(jsonPath("$.lastName").value("Updated"))
                .andExpect(jsonPath("$.phoneNumber").value("+48111222333"))
                .andExpect(jsonPath("$.address").value("Nowy adres"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/profile niepoprawne dane -> 400")
    void updateProfile_invalidData_400() throws Exception {
        String longName = "A".repeat(51);
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s"}
                                """.formatted(longName)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @DisplayName("PATCH /api/v1/users/me/profile partial update -> 200, niezmienione pola zachowane")
    void updateProfile_partialUpdate_200() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"OnlyFirst"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("OnlyFirst"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/profile bez tokena -> 403")
    void updateProfile_unauthenticated_403() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Test"}
                                """))
                .andExpect(status().isForbidden());
    }
}