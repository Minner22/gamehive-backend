package pl.m22.gamehive.game.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import pl.m22.gamehive.game.repository.GameExpansionRepository;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.support.SeededUsers;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameExpansionModerationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired GameExpansionRepository expansionRepository;
    @Autowired GameRepository gameRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @MockitoBean JavaMailSender mailSender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String moderatorToken;   // mark.moderator — ROLE_MODERATOR
    private String adminToken;       // john.doe — ROLE_ADMIN
    private String userToken;        // jane.smith — ROLE_USER

    @BeforeEach
    void setUp() {
        moderatorToken = jwtService.generateToken("mark.moderator@example.com", JwtTokenType.ACCESS, Set.of("ROLE_MODERATOR"));
        adminToken     = jwtService.generateToken("john.doe@example.com",       JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        userToken      = jwtService.generateToken("jane.smith@example.com",     JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    // ciało edycji zatwierdzonego dodatku (mutowalna mapa — pojedyncze testy podmieniają wybrane pola)
    private static Map<String, Object> editRequest() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("baseGameId", 7);
        body.put("name", "Carcassonne: Rzeka (poprawiona)");
        body.put("description", "Zaktualizowany opis biblioteczny.");
        body.put("minPlayers", null);
        body.put("maxPlayers", 8);
        body.put("playingTimeMinutes", 70);
        body.put("minAge", null);
        body.put("categoryIds", List.of(1));
        body.put("mechanicIds", List.of());
        body.put("submit", false);           // ignorowane na PUT
        return body;
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ---------- GET /moderation/expansions : kolejka + autoryzacja ----------

    @Test
    @DisplayName("GET /moderation/expansions jako MODERATOR -> 200, same PENDING + pola moderacyjne")
    void queue_asModerator_200() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].moderationStatus", everyItem(is("PENDING"))))
                .andExpect(jsonPath("$.content[*].name", hasItem("Carcassonne: Karczmy")))
                // GameExpansionModerationDto eksponuje pola moderacyjne, których GameExpansionDto nie pokazuje
                .andExpect(jsonPath("$.content[0].submittedBy").exists())
                .andExpect(jsonPath("$.content[0].resubmissionCount").exists());
    }

    @Test
    @DisplayName("GET /moderation/expansions jako ADMIN -> 200")
    void queue_asAdmin_200() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Carcassonne: Karczmy")));
    }

    @Test
    @DisplayName("GET /moderation/expansions jako USER -> 403")
    void queue_asUser_403() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /moderation/expansions bez tokena -> 401")
    void queue_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/expansions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /moderation/expansions z paginacją -> pełna strona rozmiaru 1")
    void queue_pagination_200() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
    }

    // ---------- POST /moderation/expansions/{id}/approve ----------

    @Test
    @Transactional
    @DisplayName("approve PENDING -> 200 APPROVED + reviewedBy/reviewedAt ustawione")
    void approve_pending_200() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/2/approve")     // Carcassonne: Karczmy (PENDING)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value(SeededUsers.MARK_ID.toString()))
                .andExpect(jsonPath("$.reviewedAt").exists());
    }

    @Test
    @Transactional
    @DisplayName("approve zwraca wartości dziedziczone z gry bazowej (dodatek bez nadpisań)")
    void approve_exposesInheritedValues() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/2/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseGameTitle").value("Carcassonne"))
                .andExpect(jsonPath("$.minPlayers").doesNotExist())
                .andExpect(jsonPath("$.effectiveMinPlayers").value(2))
                .andExpect(jsonPath("$.effectiveCategories[*].name", contains("Family")));
    }

    @Test
    @DisplayName("approve dodatku spoza PENDING (APPROVED) -> 409 EXPANSION_NOT_PENDING")
    void approve_notPending_409() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/1/approve")     // Carcassonne: Rzeka (APPROVED)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_PENDING"));
    }

    @Test
    @DisplayName("approve nieistniejącego dodatku -> 404 EXPANSION_NOT_FOUND")
    void approve_notFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/99999/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    @Test
    @Transactional
    @DisplayName("approve gdy gra bazowa przestała być APPROVED -> 409 BASE_GAME_NOT_APPROVED (re-check)")
    void approve_baseGameNoLongerApproved_409() throws Exception {
        // cofnięcie gry bazowej poza bibliotekę na poziomie encji — dodatek nie może dziedziczyć spoza APPROVED
        expansionRepository.findById(2L).orElseThrow()
                .getBaseGame().reject("Wycofana z biblioteki", SeededUsers.MARK_ID);

        mockMvc.perform(post("/api/v1/moderation/expansions/2/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BASE_GAME_NOT_APPROVED"));
    }

    @Test
    @DisplayName("approve jako USER -> 403")
    void approve_asUser_403() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/2/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("approve bez tokena -> 401")
    void approve_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/2/approve"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /moderation/expansions/{id}/reject ----------

    @Test
    @Transactional
    @DisplayName("reject z powodem -> 200 REJECTED + rejectionReason + reviewedBy")
    void reject_withReason_200() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/2/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Opis nie odróżnia dodatku od bazy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Opis nie odróżnia dodatku od bazy"))
                .andExpect(jsonPath("$.reviewedBy").value(SeededUsers.MARK_ID.toString()));
    }

    @Test
    @DisplayName("reject z pustym powodem -> 400 REJECTION_REASON_REQUIRED")
    void reject_blankReason_400() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/2/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REJECTION_REASON_REQUIRED"));
    }

    @Test
    @DisplayName("reject bez ciała żądania -> 400 VALIDATION_ERROR (nie 500)")
    void reject_missingBody_400() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/2/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("reject dodatku spoza PENDING (REJECTED) -> 409 EXPANSION_NOT_PENDING")
    void reject_notPending_409() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/4/reject")      // Odrzucony Dodatek Jane
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cokolwiek\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_PENDING"));
    }

    @Test
    @DisplayName("reject jako USER -> 403")
    void reject_asUser_403() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/2/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- POST /moderation/expansions/{id}/unlock ----------

    @Test
    @Transactional
    @DisplayName("unlock dodatku REJECTED na limicie -> 200 DRAFT + resubmissionCount=0 + powód wyczyszczony")
    void unlock_rejectedAtLimit_200() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/5/unlock")      // Limit Dodatku Jane (count=2=limit)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.resubmissionCount").value(0))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.reviewedBy").doesNotExist());
    }

    @Test
    @DisplayName("unlock dodatku spoza REJECTED (PENDING) -> 409 EXPANSION_NOT_REJECTED")
    void unlock_notRejected_409() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/2/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_REJECTED"));
    }

    @Test
    @DisplayName("unlock nieistniejącego dodatku -> 404 EXPANSION_NOT_FOUND")
    void unlock_notFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/99999/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("unlock jako USER -> 403")
    void unlock_asUser_403() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/expansions/5/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ---------- PUT /moderation/expansions/{id} : edycja zatwierdzonych ----------

    @Test
    @Transactional
    @DisplayName("PUT edycja APPROVED jako MODERATOR -> 200, pola podmienione, status pozostaje APPROVED")
    void edit_approvedAsModerator_200() throws Exception {
        mockMvc.perform(put("/api/v1/moderation/expansions/1")      // Carcassonne: Rzeka (APPROVED)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(editRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Carcassonne: Rzeka (poprawiona)"))
                .andExpect(jsonPath("$.maxPlayers").value(8))
                .andExpect(jsonPath("$.effectivePlayingTimeMinutes").value(70))
                .andExpect(jsonPath("$.categories[*].name", contains("Strategy")))
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"))
                // GameExpansionModerationDto eksponuje pola moderacyjne
                .andExpect(jsonPath("$.submittedBy").exists());
    }

    @Test
    @Transactional
    @DisplayName("PUT edycja APPROVED jako ADMIN -> 200")
    void edit_approvedAsAdmin_200() throws Exception {
        mockMvc.perform(put("/api/v1/moderation/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(editRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carcassonne: Rzeka (poprawiona)"));
    }

    @Test
    @Transactional
    @DisplayName("PUT edycja czyszcząca własne kategorie -> dodatek wraca do dziedziczenia z gry bazowej")
    void edit_clearingOwnCategories_fallsBackToInheritance() throws Exception {
        Map<String, Object> body = editRequest();
        body.put("categoryIds", List.of());

        mockMvc.perform(put("/api/v1/moderation/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(0)))
                .andExpect(jsonPath("$.effectiveCategories[*].name", contains("Family")));
    }

    @Test
    @DisplayName("PUT edycja dodatku nie-APPROVED (PENDING) -> 409 EXPANSION_NOT_APPROVED")
    void edit_notApproved_409() throws Exception {
        mockMvc.perform(put("/api/v1/moderation/expansions/2")      // Carcassonne: Karczmy (PENDING)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(editRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_APPROVED"));
    }

    @Test
    @DisplayName("PUT edycja nieistniejącego dodatku -> 404 EXPANSION_NOT_FOUND")
    void edit_notFound_404() throws Exception {
        mockMvc.perform(put("/api/v1/moderation/expansions/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(editRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT edycja z niespójnymi graczami -> 400 INVALID_PLAYER_COUNT (re-walidacja)")
    void edit_minGreaterThanMax_400() throws Exception {
        Map<String, Object> body = editRequest();
        body.put("minPlayers", 9);

        mockMvc.perform(put("/api/v1/moderation/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PLAYER_COUNT"));
    }

    @Test
    @DisplayName("PUT edycja jako USER -> 403")
    void edit_asUser_403() throws Exception {
        mockMvc.perform(put("/api/v1/moderation/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(editRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT edycja bez tokena -> 401")
    void edit_unauthenticated_401() throws Exception {
        mockMvc.perform(put("/api/v1/moderation/expansions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(editRequest())))
                .andExpect(status().isUnauthorized());
    }

    // ---------- DELETE /moderation/expansions/{id} : twardy delete ----------

    @Test
    @Transactional
    @DisplayName("DELETE dodatku APPROVED jako MODERATOR -> 204, dodatek znika, gra bazowa zostaje")
    void delete_approved_204() throws Exception {
        mockMvc.perform(delete("/api/v1/moderation/expansions/1")   // Carcassonne: Rzeka (APPROVED)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isNoContent());

        assertThat(expansionRepository.findById(1L)).isEmpty();
        assertThat(gameRepository.findById(7L)).isPresent();
    }

    @Test
    @Transactional
    @DisplayName("DELETE dodatku PENDING -> 204 (wariant C: dowolny status oprócz DRAFT)")
    void delete_pending_204() throws Exception {
        mockMvc.perform(delete("/api/v1/moderation/expansions/2")   // Carcassonne: Karczmy (PENDING)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isNoContent());

        assertThat(expansionRepository.findById(2L)).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("DELETE dodatku REJECTED jako ADMIN -> 204")
    void delete_rejectedAsAdmin_204() throws Exception {
        mockMvc.perform(delete("/api/v1/moderation/expansions/4")   // Odrzucony Dodatek Jane (REJECTED)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(expansionRepository.findById(4L)).isEmpty();
    }

    @Test
    @DisplayName("DELETE dodatku DRAFT -> 404 EXPANSION_NOT_FOUND (prywatny szkic niewidoczny dla moderatora)")
    void delete_draft_404() throws Exception {
        mockMvc.perform(delete("/api/v1/moderation/expansions/3")   // Szkic Dodatku Jane (DRAFT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE nieistniejącego dodatku -> 404 EXPANSION_NOT_FOUND")
    void delete_notFound_404() throws Exception {
        mockMvc.perform(delete("/api/v1/moderation/expansions/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE jako USER -> 403")
    void delete_asUser_403() throws Exception {
        mockMvc.perform(delete("/api/v1/moderation/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE bez tokena -> 401")
    void delete_unauthenticated_401() throws Exception {
        mockMvc.perform(delete("/api/v1/moderation/expansions/1"))
                .andExpect(status().isUnauthorized());
    }
}
