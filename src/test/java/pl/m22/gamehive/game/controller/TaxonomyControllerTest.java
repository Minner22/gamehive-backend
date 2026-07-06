package pl.m22.gamehive.game.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;

import java.util.Objects;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaxonomyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @MockitoBean JavaMailSender mailSender;

    private String userToken;

    @BeforeEach
    void setUp() {
        userToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("GET /taxonomy/categories jako USER -> 200 + lista")
    void listCategories_asUser_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    @DisplayName("GET /taxonomy/mechanics jako USER -> 200 + lista")
    void listMechanics_asUser_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/mechanics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))));
    }

    @Test
    @DisplayName("GET /taxonomy/publishers jako USER -> 200 + wszyscy")
    void listPublishers_asUser_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("GET /taxonomy/publishers?status=APPROVED -> 200 + tylko APPROVED")
    void listPublishers_filterApproved_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].status", everyItem(is("APPROVED"))));
    }

    @Test
    @DisplayName("GET /taxonomy/authors jako USER -> 200 + lista")
    void listAuthors_asUser_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/authors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].firstName").exists())
                .andExpect(jsonPath("$[0].lastName").exists());
    }

    @Test
    @DisplayName("GET /taxonomy/authors?status=APPROVED -> 200 + tylko APPROVED")
    void listAuthors_filterApproved_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/authors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", everyItem(is("APPROVED"))));
    }

    @Test
    @DisplayName("GET /taxonomy/categories bez tokena -> 401")
    void listCategories_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/categories"))
                .andExpect(status().isUnauthorized());
    }
}