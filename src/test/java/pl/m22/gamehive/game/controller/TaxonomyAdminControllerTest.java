package pl.m22.gamehive.game.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class TaxonomyAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @MockitoBean JavaMailSender mailSender;

    private String adminToken;
    private String moderatorToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        adminToken     = jwtService.generateToken("john.doe@example.com",   JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        moderatorToken = jwtService.generateToken("mark.moderator@example.com", JwtTokenType.ACCESS, Set.of("ROLE_MODERATOR"));
        userToken      = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    // ---------- CATEGORIES: autoryzacja ----------

    @Test
    @DisplayName("GET /categories jako MODERATOR -> 200 + lista")
    void listCategories_asModerator_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    @DisplayName("GET /categories jako USER -> 403")
    void listCategories_asUser_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /categories bez tokena -> 401")
    void listCategories_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/taxonomy/categories"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- CATEGORIES: CRUD happy path + walidacje ----------

    @Test
    @Transactional
    @DisplayName("POST /categories jako ADMIN -> 201 + utworzona kategoria")
    void createCategory_asAdmin_201() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Eurogame\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Eurogame"));
    }

    @Test
    @DisplayName("POST /categories duplikat nazwy -> 409 (CATEGORY_NAME_EXISTS)")
    void createCategory_duplicate_409() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Strategy\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NAME_EXISTS"));
    }

    @Test
    @DisplayName("POST /categories pusta nazwa -> 400 (walidacja)")
    void createCategory_blank_400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @DisplayName("PUT /categories/{id} jako ADMIN -> 200 + nowa nazwa")
    void renameCategory_asAdmin_200() throws Exception {
        mockMvc.perform(put("/api/v1/admin/taxonomy/categories/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Strategy Reworked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Strategy Reworked"));
    }

    @Test
    @DisplayName("PUT /categories/{id} nieistniejący -> 404 (CATEGORY_NOT_FOUND)")
    void renameCategory_notFound_404() throws Exception {
        mockMvc.perform(put("/api/v1/admin/taxonomy/categories/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Whatever\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /categories/{id} nazwa zajęta przez inny wpis -> 409")
    void renameCategory_duplicate_409() throws Exception {
        mockMvc.perform(put("/api/v1/admin/taxonomy/categories/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Family\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NAME_EXISTS"));
    }

    @Test
    @Transactional
    @DisplayName("DELETE /categories/{id} jako ADMIN -> 204")
    void deleteCategory_asAdmin_204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/taxonomy/categories/4")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /categories/{id} nieistniejący -> 404 (CATEGORY_NOT_FOUND)")
    void deleteCategory_notFound_404() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/taxonomy/categories/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NOT_FOUND"));
    }

    // ---------- MECHANICS: reprezentatywny zestaw (analogicznie do kategorii) ----------

    @Test
    @DisplayName("GET /mechanics jako MODERATOR -> 200 + lista")
    void listMechanics_asModerator_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/taxonomy/mechanics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))));
    }

    @Test
    @Transactional
    @DisplayName("POST /mechanics jako MODERATOR -> 201")
    void createMechanic_asModerator_201() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/mechanics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tile Placement\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tile Placement"));
    }

    @Test
    @DisplayName("POST /mechanics duplikat -> 409 (MECHANIC_NAME_EXISTS)")
    void createMechanic_duplicate_409() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/mechanics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Area Control\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MECHANIC_NAME_EXISTS"));
    }

    @Test
    @DisplayName("DELETE /mechanics/{id} nieistniejący -> 404 (MECHANIC_NOT_FOUND)")
    void deleteMechanic_notFound_404() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/taxonomy/mechanics/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MECHANIC_NOT_FOUND"));
    }

    // ---------- PUBLISHERS: lista + filtr + create + approve + delete ----------

    @Test
    @DisplayName("GET /publishers bez filtra -> 200 + wszyscy (>=3)")
    void listPublishers_all_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/taxonomy/publishers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("GET /publishers?status=PENDING -> 200 + tylko PENDING")
    void listPublishers_filterPending_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/taxonomy/publishers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[*].status", everyItem(is("PENDING"))));
    }

    @Test
    @Transactional
    @DisplayName("POST /publishers jako ADMIN -> 201 + status APPROVED")
    void createPublisher_asAdmin_201() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/publishers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stonemaier Games\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Stonemaier Games"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /publishers duplikat -> 409 (PUBLISHER_NAME_EXISTS)")
    void createPublisher_duplicate_409() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/publishers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rio Grande Games\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PUBLISHER_NAME_EXISTS"));
    }

    @Test
    @Transactional
    @DisplayName("POST /publishers/{id}/approve PENDING -> 200 + APPROVED")
    void approvePublisher_pending_200() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/publishers/3/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @Transactional
    @DisplayName("POST /publishers/{id}/approve już APPROVED -> 200 (idempotentne)")
    void approvePublisher_alreadyApproved_200() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/publishers/1/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /publishers/{id}/approve nieistniejący -> 404 (PUBLISHER_NOT_FOUND)")
    void approvePublisher_notFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/publishers/99999/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PUBLISHER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /publishers/{id}/approve jako USER -> 403")
    void approvePublisher_asUser_403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/publishers/3/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /publishers/{id}/approve bez tokena -> 401")
    void approvePublisher_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/publishers/3/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    @DisplayName("DELETE /publishers/{id} jako ADMIN -> 204")
    void deletePublisher_asAdmin_204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/taxonomy/publishers/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /publishers/{id} nieistniejący -> 404 (PUBLISHER_NOT_FOUND)")
    void deletePublisher_notFound_404() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/taxonomy/publishers/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PUBLISHER_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /publishers/{id} jako USER -> 403")
    void deletePublisher_asUser_403() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/taxonomy/publishers/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ---------- AUTHORS: lista + tworzenie + edycja + usuwanie ----------

    @Test
    @DisplayName("GET /authors jako MODERATOR -> 200 + lista (>=2)")
    void listAuthors_asModerator_200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/taxonomy/authors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].firstName").exists())
                .andExpect(jsonPath("$[0].lastName").exists());
    }

    @Test
    @DisplayName("GET /authors jako USER -> 403")
    void listAuthors_asUser_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/taxonomy/authors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    @DisplayName("POST /authors jako MODERATOR -> 201")
    void createAuthor_asModerator_201() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/authors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Vital\",\"lastName\":\"Lacerda\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.firstName").value("Vital"))
                .andExpect(jsonPath("$.lastName").value("Lacerda"));
    }

    @Test
    @DisplayName("POST /authors duplikat pary -> 409 (AUTHOR_NAME_EXISTS)")
    void createAuthor_duplicate_409() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/authors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Uwe\",\"lastName\":\"Rosenberg\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AUTHOR_NAME_EXISTS"));
    }

    @Test
    @DisplayName("POST /authors jako USER -> 403")
    void createAuthor_asUser_403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/taxonomy/authors")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Vital\",\"lastName\":\"Lacerda\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    @DisplayName("PUT /authors/{id} jako ADMIN -> 200 + zaktualizowane imię/nazwisko")
    void updateAuthor_asAdmin_200() throws Exception {
        mockMvc.perform(put("/api/v1/admin/taxonomy/authors/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Uwe\",\"lastName\":\"Rosenburg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Uwe"))
                .andExpect(jsonPath("$.lastName").value("Rosenburg"));
    }

    @Test
    @DisplayName("PUT /authors/{id} nieistniejący -> 404 (AUTHOR_NOT_FOUND)")
    void updateAuthor_notFound_404() throws Exception {
        mockMvc.perform(put("/api/v1/admin/taxonomy/authors/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ghost\",\"lastName\":\"Author\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AUTHOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /authors/{id} para zajęta przez innego autora -> 409 (AUTHOR_NAME_EXISTS)")
    void updateAuthor_duplicate_409() throws Exception {
        // autor 1 (Uwe Rosenberg) na parę autora 2 (Reiner Knizia)
        mockMvc.perform(put("/api/v1/admin/taxonomy/authors/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Reiner\",\"lastName\":\"Knizia\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AUTHOR_NAME_EXISTS"));
    }

    @Test
    @DisplayName("PUT /authors/{id} pusta nazwa -> 400 (walidacja)")
    void updateAuthor_blank_400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/taxonomy/authors/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"Rosenberg\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @DisplayName("DELETE /authors/{id} jako ADMIN -> 204")
    void deleteAuthor_asAdmin_204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/taxonomy/authors/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /authors/{id} nieistniejący -> 404 (AUTHOR_NOT_FOUND)")
    void deleteAuthor_notFound_404() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/taxonomy/authors/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AUTHOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /authors/{id} bez tokena -> 401")
    void deleteAuthor_unauthenticated_401() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/taxonomy/authors/2"))
                .andExpect(status().isUnauthorized());
    }
}