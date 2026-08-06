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
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.model.GameExpansion;
import pl.m22.gamehive.game.repository.GameExpansionRepository;
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
class GameExpansionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired GameExpansionRepository expansionRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @MockitoBean JavaMailSender mailSender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Jane: dodatki 1 (APPROVED), 2 (PENDING), 3 (DRAFT), 4 (REJECTED count=1), 5 (REJECTED count=2 = limit testowy);
    // dodatek 6 należy do Johna. Gra bazowa wszystkich: 7 (Carcassonne, APPROVED, 2..2 graczy, 45 min, wiek 8)
    private String janeToken;
    private String johnToken;   // inny użytkownik — do testu „cudzy dodatek APPROVED widoczny dla każdego"

    @BeforeEach
    void setUp() {
        janeToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        johnToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    // mutowalna mapa zamiast szablonu String + replace(): literówka w kluczu = zły JSON = czerwony test
    private static Map<String, Object> validRequest(boolean submit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("baseGameId", 7);
        body.put("name", "Carcassonne: Kupcy");
        body.put("description", "Dodatek wprowadzający towary.");
        body.put("minPlayers", null);          // null = dziedziczy z gry bazowej
        body.put("maxPlayers", 5);
        body.put("playingTimeMinutes", null);
        body.put("minAge", null);
        body.put("categoryIds", List.of(1));
        body.put("mechanicIds", List.of(2));
        body.put("submit", submit);
        return body;
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ---------- POST /api/v1/expansions: happy path ----------

    @Test
    @Transactional
    @DisplayName("POST /expansions submit=false -> 201 DRAFT, wartości własne i efektywne w DTO")
    void createExpansion_draft_201() throws Exception {
        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Carcassonne: Kupcy"))
                .andExpect(jsonPath("$.baseGameId").value(7))
                .andExpect(jsonPath("$.baseGameTitle").value("Carcassonne"))
                .andExpect(jsonPath("$.moderationStatus").value("DRAFT"))
                // nadpisane
                .andExpect(jsonPath("$.maxPlayers").value(5))
                .andExpect(jsonPath("$.effectiveMaxPlayers").value(5))
                // dziedziczone (własne null, efektywne z gry 7)
                .andExpect(jsonPath("$.minPlayers").doesNotExist())
                .andExpect(jsonPath("$.effectiveMinPlayers").value(2))
                .andExpect(jsonPath("$.effectivePlayingTimeMinutes").value(45))
                .andExpect(jsonPath("$.effectiveMinAge").value(8))
                // własne kolekcje nadpisują bazowe
                .andExpect(jsonPath("$.categories[*].name", contains("Strategy")))
                .andExpect(jsonPath("$.effectiveCategories[*].name", contains("Strategy")))
                .andExpect(jsonPath("$.mechanics[*].name", contains("Deck-building")))
                .andExpect(jsonPath("$.effectiveMechanics[*].name", contains("Deck-building")));

        // submittedBy nie jest eksponowane w GameExpansionDto — weryfikacja przez repozytorium
        GameExpansion created = expansionRepository.findByName("Carcassonne: Kupcy").getFirst();
        assertThat(created.getSubmittedBy()).isEqualTo(SeededUsers.JANE_ID);
        assertThat(created.getResubmissionCount()).isZero();
    }

    @Test
    @Transactional
    @DisplayName("POST /expansions submit=true -> 201 PENDING")
    void createExpansion_submit_201() throws Exception {
        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moderationStatus").value("PENDING"));
    }

    @Test
    @Transactional
    @DisplayName("POST /expansions bez żadnych nadpisań -> wszystkie wartości efektywne z gry bazowej")
    void createExpansion_noOverrides_inheritsEverything() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("maxPlayers", null);
        body.put("categoryIds", List.of());
        body.put("mechanicIds", List.of());

        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effectiveMinPlayers").value(2))
                .andExpect(jsonPath("$.effectiveMaxPlayers").value(2))
                .andExpect(jsonPath("$.categories", hasSize(0)))
                .andExpect(jsonPath("$.effectiveCategories[*].name", contains("Family")))
                .andExpect(jsonPath("$.mechanics", hasSize(0)))
                .andExpect(jsonPath("$.effectiveMechanics[*].name", contains("Area Control")));
    }

    @Test
    @DisplayName("POST /expansions bez tokena -> 401")
    void createExpansion_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/v1/expansions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(false))))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /api/v1/expansions: walidacje ----------

    @Test
    @DisplayName("POST /expansions do gry NIE-APPROVED -> 409 (BASE_GAME_NOT_APPROVED)")
    void createExpansion_baseGameNotApproved_409() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("baseGameId", 2);          // Pandemic — PENDING

        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BASE_GAME_NOT_APPROVED"));
    }

    @Test
    @DisplayName("POST /expansions do nieistniejącej gry -> 404 (GAME_NOT_FOUND)")
    void createExpansion_baseGameNotFound_404() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("baseGameId", 99999);

        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /expansions własne minPlayers > własne maxPlayers -> 400 (INVALID_PLAYER_COUNT)")
    void createExpansion_ownMinGreaterThanOwnMax_400() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("minPlayers", 7);
        body.put("maxPlayers", 5);

        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PLAYER_COUNT"));
    }

    @Test
    @DisplayName("POST /expansions własne minPlayers > DZIEDZICZONE maxPlayers -> 400 (walidacja wartości efektywnych)")
    void createExpansion_ownMinGreaterThanInheritedMax_400() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("minPlayers", 4);          // gra 7 ma maxPlayers = 2, a dodatek go nie nadpisuje
        body.put("maxPlayers", null);

        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PLAYER_COUNT"));
    }

    @Test
    @DisplayName("POST /expansions pusta nazwa -> 400 (Bean Validation)")
    void createExpansion_blankName_400() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("name", "  ");

        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /expansions bez baseGameId -> 400 (Bean Validation)")
    void createExpansion_missingBaseGameId_400() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("baseGameId", null);

        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /expansions nieistniejący categoryId -> 404 (CATEGORY_NOT_FOUND)")
    void createExpansion_unknownCategoryId_404() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("categoryIds", List.of(99999));

        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /expansions nieistniejący mechanicId -> 404 (MECHANIC_NOT_FOUND)")
    void createExpansion_unknownMechanicId_404() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("mechanicIds", List.of(99999));

        mockMvc.perform(post("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MECHANIC_NOT_FOUND"));
    }

    // ---------- GET /api/v1/expansions : biblioteka (tylko APPROVED) ----------

    @Test
    @DisplayName("GET /expansions -> 200, tylko dodatki APPROVED (PENDING/REJECTED/DRAFT pominięte)")
    void library_returnsOnlyApproved_200() throws Exception {
        mockMvc.perform(get("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].name", contains("Carcassonne: Rzeka")))
                .andExpect(jsonPath("$.content[*].moderationStatus", everyItem(is("APPROVED"))));
    }

    @Test
    @DisplayName("GET /expansions -> pozycja biblioteki niesie wartości własne i efektywne")
    void library_exposesOwnAndEffectiveValues() throws Exception {
        mockMvc.perform(get("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].maxPlayers").value(6))              // nadpisane
                .andExpect(jsonPath("$.content[0].effectiveMaxPlayers").value(6))
                .andExpect(jsonPath("$.content[0].minPlayers").doesNotExist())        // dziedziczone
                .andExpect(jsonPath("$.content[0].effectiveMinPlayers").value(2))
                .andExpect(jsonPath("$.content[0].baseGameTitle").value("Carcassonne"));
    }

    @Test
    @DisplayName("GET /expansions?baseGameId=7 -> Rzeka; ?baseGameId=1 -> pusto (Agricola bez dodatków)")
    void library_filterByBaseGame() throws Exception {
        mockMvc.perform(get("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("baseGameId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", contains("Carcassonne: Rzeka")));

        mockMvc.perform(get("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("baseGameId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /expansions?categoryId=5 -> Rzeka (własna kategoria); ?categoryId=2 -> pusto (dziedziczona nie filtruje)")
    void library_filterByOwnCategory() throws Exception {
        mockMvc.perform(get("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("categoryId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", contains("Carcassonne: Rzeka")));

        // filtr działa po WŁASNYCH kolekcjach dodatku — dziedziczona Family (2) go nie łapie
        mockMvc.perform(get("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("categoryId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /expansions?mechanicId=3 -> pusto (Area Control jest dziedziczona, nie własna)")
    void library_filterByMechanic_ignoresInherited() throws Exception {
        mockMvc.perform(get("/api/v1/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("mechanicId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /expansions bez tokena -> 401")
    void library_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/expansions"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET /api/v1/expansions/{id} ----------

    @Test
    @DisplayName("GET /expansions/{id} własny DRAFT -> 200 + pełne DTO")
    void getExpansion_ownDraft_200() throws Exception {
        mockMvc.perform(get("/api/v1/expansions/3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Szkic Dodatku Jane"))
                .andExpect(jsonPath("$.moderationStatus").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /expansions/{id} własny APPROVED -> 200 (właściciel widzi własny dodatek w każdym statusie)")
    void getExpansion_ownApproved_200() throws Exception {
        mockMvc.perform(get("/api/v1/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carcassonne: Rzeka"))
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /expansions/{id} cudzy dodatek APPROVED -> 200 (biblioteka widoczna dla każdego zalogowanego)")
    void getExpansion_othersApproved_200() throws Exception {
        // dodatek 1 należy do Jane; John widzi go, bo jest APPROVED
        mockMvc.perform(get("/api/v1/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carcassonne: Rzeka"));
    }

    @Test
    @DisplayName("GET /expansions/{id} cudze zgłoszenie -> 404 (EXPANSION_NOT_FOUND, bez ujawniania istnienia)")
    void getExpansion_notOwner_404() throws Exception {
        mockMvc.perform(get("/api/v1/expansions/6")     // szkic Johna
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /expansions/{id} nieistniejący -> 404 (EXPANSION_NOT_FOUND)")
    void getExpansion_notFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/expansions/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /expansions/{id} bez tokena -> 401")
    void getExpansion_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/expansions/1"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET /api/v1/expansions/my ----------

    @Test
    @DisplayName("GET /expansions/my -> 200, tylko własne DRAFT/PENDING/REJECTED, bez APPROVED")
    void mySubmissions_returnsOwnNonApproved_200() throws Exception {
        mockMvc.perform(get("/api/v1/expansions/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder(
                        "Carcassonne: Karczmy", "Szkic Dodatku Jane", "Odrzucony Dodatek Jane", "Limit Dodatku Jane")))
                .andExpect(jsonPath("$.content[*].moderationStatus", not(hasItem("APPROVED"))));
    }

    @Test
    @DisplayName("GET /expansions/my z paginacją -> rozmiar strony respektowany")
    void mySubmissions_pagination_200() throws Exception {
        mockMvc.perform(get("/api/v1/expansions/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("GET /expansions/my bez tokena -> 401")
    void mySubmissions_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/expansions/my"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- PUT /api/v1/expansions/{id} ----------

    @Test
    @Transactional
    @DisplayName("PUT /expansions/{id} własny DRAFT -> 200, pola i relacje podmienione, status bez zmian")
    void updateExpansion_ownDraft_200() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("name", "Szkic Dodatku Jane v2");

        mockMvc.perform(put("/api/v1/expansions/3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Szkic Dodatku Jane v2"))
                .andExpect(jsonPath("$.moderationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.maxPlayers").value(5))
                .andExpect(jsonPath("$.categories[*].name", contains("Strategy")));
    }

    @Test
    @Transactional
    @DisplayName("PUT /expansions/{id} własny REJECTED -> 200, status pozostaje REJECTED")
    void updateExpansion_ownRejected_200() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("name", "Odrzucony Dodatek Jane v2");

        mockMvc.perform(put("/api/v1/expansions/4")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Odrzucony Dodatek Jane v2"))
                .andExpect(jsonPath("$.moderationStatus").value("REJECTED"));
    }

    @Test
    @Transactional
    @DisplayName("PUT /expansions/{id} ignoruje baseGameId — dodatek zostaje przy swojej grze bazowej")
    void updateExpansion_baseGameIdIgnored() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("baseGameId", 1);          // Agricola (też APPROVED) — nie może zmienić bazy

        mockMvc.perform(put("/api/v1/expansions/3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseGameId").value(7))
                .andExpect(jsonPath("$.baseGameTitle").value("Carcassonne"));
    }

    @Test
    @Transactional
    @DisplayName("PUT /expansions/{id} niespójne wartości efektywne -> 400 (INVALID_PLAYER_COUNT), stan nietknięty")
    void updateExpansion_minGreaterThanMax_400() throws Exception {
        Map<String, Object> body = validRequest(false);
        body.put("minPlayers", 9);

        mockMvc.perform(put("/api/v1/expansions/3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PLAYER_COUNT"));

        // walidacja odrzuca żądanie zanim cokolwiek zostanie nadpisane
        GameExpansion untouched = expansionRepository.findById(3L).orElseThrow();
        assertThat(untouched.getName()).isEqualTo("Szkic Dodatku Jane");
        assertThat(untouched.getMinPlayers()).isNull();
    }

    @Test
    @DisplayName("PUT /expansions/{id} własny PENDING -> 409 (EXPANSION_NOT_EDITABLE)")
    void updateExpansion_ownPending_409() throws Exception {
        mockMvc.perform(put("/api/v1/expansions/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("PUT /expansions/{id} cudze zgłoszenie -> 404 (EXPANSION_NOT_FOUND, bez ujawniania istnienia)")
    void updateExpansion_notOwner_404() throws Exception {
        mockMvc.perform(put("/api/v1/expansions/6")     // szkic Johna
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /expansions/{id} nieistniejący -> 404 (EXPANSION_NOT_FOUND)")
    void updateExpansion_notFound_404() throws Exception {
        mockMvc.perform(put("/api/v1/expansions/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest(false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    // ---------- POST /api/v1/expansions/{id}/submit ----------

    @Test
    @Transactional
    @DisplayName("POST /expansions/{id}/submit z DRAFT -> 200 PENDING, count bez zmian")
    void submitExpansion_draft_200() throws Exception {
        mockMvc.perform(post("/api/v1/expansions/3/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("PENDING"));

        assertThat(expansionRepository.findById(3L).orElseThrow().getResubmissionCount()).isZero();
    }

    @Test
    @Transactional
    @DisplayName("POST /expansions/{id}/submit z REJECTED -> 200 PENDING, count+1, powód wyczyszczony")
    void submitExpansion_rejected_200_incrementsCount() throws Exception {
        mockMvc.perform(post("/api/v1/expansions/4/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("PENDING"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        GameExpansion resubmitted = expansionRepository.findById(4L).orElseThrow();
        assertThat(resubmitted.getResubmissionCount()).isEqualTo(2);
        assertThat(resubmitted.getReviewedBy()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("POST /expansions/{id}/submit po wyczerpaniu limitu -> 409 (RESUBMISSION_LIMIT_EXCEEDED), status bez zmian")
    void submitExpansion_limitExceeded_409() throws Exception {
        // limit testowy = 2 (application-test.yml), dodatek 5 ma resubmission_count = 2
        mockMvc.perform(post("/api/v1/expansions/5/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESUBMISSION_LIMIT_EXCEEDED"));

        GameExpansion blocked = expansionRepository.findById(5L).orElseThrow();
        assertThat(blocked.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(blocked.getResubmissionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("POST /expansions/{id}/submit z PENDING -> 409 (EXPANSION_NOT_EDITABLE)")
    void submitExpansion_pending_409() throws Exception {
        mockMvc.perform(post("/api/v1/expansions/2/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("POST /expansions/{id}/submit cudze zgłoszenie -> 404 (EXPANSION_NOT_FOUND)")
    void submitExpansion_notOwner_404() throws Exception {
        mockMvc.perform(post("/api/v1/expansions/6/submit")     // szkic Johna
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /expansions/{id}/submit bez tokena -> 401")
    void submitExpansion_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/v1/expansions/3/submit"))
                .andExpect(status().isUnauthorized());
    }
}
