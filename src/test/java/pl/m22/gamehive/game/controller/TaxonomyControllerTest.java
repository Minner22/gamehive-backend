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

    // ---------- podpowiedzi (GH-131): serwis NIE jest podmieniony, więc to realne wyniki fallbacku ----------

    @Test
    @DisplayName("GET /taxonomy/publishers/suggest?q=grande -> 200 + realne dopasowanie po fragmencie nazwy")
    void suggestPublishers_matchesNameFragment_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "grande"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Rio Grande Games"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /taxonomy/publishers/suggest jest niewrażliwe na wielkość liter")
    void suggestPublishers_ignoresCase_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "GRANDE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Rio Grande Games"));
    }

    @Test
    @DisplayName("GET /taxonomy/publishers/suggest?q=pending -> podpowiedź zawiera wpis PENDING ze statusem")
    void suggestPublishers_includesPending_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Pending Games"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /taxonomy/publishers/suggest?q=games&limit=2 -> limit ucina (3 zasianych wydawców pasuje)")
    void suggestPublishers_respectsLimit_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));

        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "games")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /taxonomy/publishers/suggest bez dopasowania -> 200 + pusta lista")
    void suggestPublishers_noMatch_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "nieistniejacy wydawca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /taxonomy/authors/suggest?q=uwe -> dopasowanie po imieniu")
    void suggestAuthors_byFirstName_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/authors/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "uwe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firstName").value("Uwe"))
                .andExpect(jsonPath("$[0].lastName").value("Rosenberg"));
    }

    @Test
    @DisplayName("GET /taxonomy/authors/suggest?q=knizia -> dopasowanie po nazwisku")
    void suggestAuthors_byLastName_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/authors/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "knizia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firstName").value("Reiner"));
    }

    @Test
    @DisplayName("GET /taxonomy/authors/suggest?q=uwe rosen -> dopasowanie po pełnej frazie 'Imię Nazwisko'")
    void suggestAuthors_byFullName_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/authors/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "uwe rosen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lastName").value("Rosenberg"));
    }

    @Test
    @DisplayName("GET /taxonomy/authors/suggest?q=oczekujacy -> autor PENDING jest widoczny")
    void suggestAuthors_includesPending_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/authors/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "oczekujacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /taxonomy/authors/suggest bez tokena -> 401")
    void suggestAuthors_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/authors/suggest").param("q", "uwe"))
                .andExpect(status().isUnauthorized());
    }
}