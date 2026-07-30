package pl.m22.gamehive.game.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
import pl.m22.gamehive.support.SeededUsers;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameModerationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
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

    // buduje PENDING grę z wydawcą i autorem PENDING (przez find-or-create #117) i zwraca jej id;
    // dzięki temu test kaskady nie zależy od fixtur data.sql (i nie psuje GameRepositoryTest)
    private long createPendingGameWithPendingTaxonomy() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Kandydat do moderacji");
        body.put("description", "Gra oczekująca na decyzję.");
        body.put("minPlayers", 2);
        body.put("maxPlayers", 4);
        body.put("playingTimeMinutes", 60);
        body.put("yearPublished", 2021);
        body.put("minAge", 10);
        body.put("publisherIds", List.of(1));                       // Rio Grande (APPROVED)
        body.put("newPublisherNames", List.of("Nowy Wydawca Mod")); // -> PENDING
        body.put("categoryIds", List.of(1));
        body.put("mechanicIds", List.of());
        body.put("authorIds", List.of(2));                          // Reiner Knizia (APPROVED)
        body.put("newAuthors", List.of(Map.of("firstName", "Nowy", "lastName", "AutorMod"))); // -> PENDING
        body.put("submit", true);                                   // od razu PENDING

        String json = mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moderationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // ---------- GET /moderation/games : kolejka + autoryzacja ----------

    @Test
    @DisplayName("GET /moderation/games jako MODERATOR -> 200, same PENDING (m.in. Pandemic)")
    void queue_asModerator_200() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].moderationStatus", everyItem(is("PENDING"))))
                .andExpect(jsonPath("$.content[*].title", hasItem("Pandemic")))
                // GameModerationDto eksponuje pola moderacyjne, których GameDto nie pokazuje
                .andExpect(jsonPath("$.content[0].submittedBy").exists())
                .andExpect(jsonPath("$.content[0].resubmissionCount").exists());
    }

    @Test
    @DisplayName("GET /moderation/games jako ADMIN -> 200")
    void queue_asAdmin_200() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /moderation/games jako USER -> 403")
    void queue_asUser_403() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /moderation/games bez tokena -> 401")
    void queue_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/games"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /moderation/games z paginacją -> rozmiar strony respektowany")
    void queue_pagination_200() throws Exception {
        mockMvc.perform(get("/api/v1/moderation/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(lessThanOrEqualTo(1))));
    }

    // ---------- POST /moderation/games/{id}/approve ----------

    @Test
    @Transactional
    @DisplayName("approve PENDING -> 200 APPROVED + reviewedBy/reviewedAt ustawione")
    void approve_pending_200() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/2/approve")     // Pandemic (PENDING, Jane)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedBy").value(SeededUsers.MARK_ID.toString()))
                .andExpect(jsonPath("$.reviewedAt").exists());
    }

    @Test
    @Transactional
    @DisplayName("approve zatwierdza WSZYSTKICH wydawców i autorów PENDING gry, APPROVED zostają bez zmian")
    void approve_cascadesPendingPublishersAndAuthors() throws Exception {
        long id = createPendingGameWithPendingTaxonomy();

        mockMvc.perform(post("/api/v1/moderation/games/" + id + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"))
                // wcześniej PENDING -> teraz APPROVED
                .andExpect(jsonPath("$.publishers[?(@.name=='Nowy Wydawca Mod')].status", contains("APPROVED")))
                .andExpect(jsonPath("$.authors[?(@.lastName=='AutorMod')].status", contains("APPROVED")))
                // były APPROVED -> nadal APPROVED
                .andExpect(jsonPath("$.publishers[?(@.name=='Rio Grande Games')].status", contains("APPROVED")))
                .andExpect(jsonPath("$.authors[?(@.lastName=='Knizia')].status", contains("APPROVED")));
    }

    @Test
    @DisplayName("approve gry spoza PENDING (APPROVED) -> 409 GAME_NOT_PENDING")
    void approve_notPending_409() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/1/approve")     // Agricola (APPROVED)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_PENDING"));
    }

    @Test
    @DisplayName("approve nieistniejącej gry -> 404 GAME_NOT_FOUND")
    void approve_notFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/99999/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("approve jako USER -> 403")
    void approve_asUser_403() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/2/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("approve bez tokena -> 401")
    void approve_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/2/approve"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /moderation/games/{id}/reject ----------

    @Test
    @Transactional
    @DisplayName("reject z powodem -> 200 REJECTED + rejectionReason + reviewedBy")
    void reject_withReason_200() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/2/reject")      // Pandemic (PENDING)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Brak zdjęć komponentów\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Brak zdjęć komponentów"))
                .andExpect(jsonPath("$.reviewedBy").value(SeededUsers.MARK_ID.toString()));
    }

    @Test
    @DisplayName("reject z pustym powodem -> 400 REJECTION_REASON_REQUIRED")
    void reject_blankReason_400() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/2/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REJECTION_REASON_REQUIRED"));
    }

    @Test
    @DisplayName("reject gry spoza PENDING (REJECTED) -> 409 GAME_NOT_PENDING")
    void reject_notPending_409() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/3/reject")      // Odrzucona Gra (REJECTED)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cokolwiek\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_PENDING"));
    }

    @Test
    @DisplayName("reject jako USER -> 403")
    void reject_asUser_403() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/2/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- POST /moderation/games/{id}/unlock ----------

    @Test
    @Transactional
    @DisplayName("unlock gry REJECTED na limicie -> 200 DRAFT + resubmissionCount=0 + powód wyczyszczony")
    void unlock_rejectedAtLimit_200() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/6/unlock")      // Limit Jane (REJECTED, count=2=limit)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.resubmissionCount").value(0))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.reviewedBy").doesNotExist());
    }

    @Test
    @DisplayName("unlock gry spoza REJECTED (PENDING) -> 409 GAME_NOT_REJECTED")
    void unlock_notRejected_409() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/2/unlock")      // Pandemic (PENDING)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_REJECTED"));
    }

    @Test
    @DisplayName("unlock jako USER -> 403")
    void unlock_asUser_403() throws Exception {
        mockMvc.perform(post("/api/v1/moderation/games/6/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
