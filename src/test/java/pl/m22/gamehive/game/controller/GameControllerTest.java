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
import pl.m22.gamehive.common.persistence.ModerationStatus;
import pl.m22.gamehive.game.model.Game;
import pl.m22.gamehive.game.repository.GameRepository;
import pl.m22.gamehive.support.SeededUsers;

import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired GameRepository gameRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @MockitoBean JavaMailSender mailSender;

    // Jane: gry 1 (APPROVED), 2 (PENDING), 4 (DRAFT), 5 (REJECTED, count=1), 6 (REJECTED, count=2 = limit testowy);
    // gra 3 należy do Johna — „cudze zgłoszenie" z perspektywy Jane
    private String janeToken;

    @BeforeEach
    void setUp() {
        janeToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    private static String validBody(boolean submit) {
        return """
                {
                  "title": "Terraforming Mars",
                  "description": "Kolonizacja Marsa w rywalizacji korporacji.",
                  "minPlayers": 1,
                  "maxPlayers": 5,
                  "playingTimeMinutes": 120,
                  "yearPublished": 2016,
                  "minAge": 12,
                  "publisherIds": [1],
                  "newPublisherNames": [],
                  "categoryIds": [1],
                  "mechanicIds": [2],
                  "authorIds": [2],
                  "newAuthors": [],
                  "submit": %s
                }""".formatted(submit);
    }

    // ---------- POST /api/v1/games: happy path ----------

    @Test
    @Transactional
    @DisplayName("POST /games submit=false -> 201 + DRAFT z relacjami")
    void createGame_draft_201() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Terraforming Mars"))
                .andExpect(jsonPath("$.moderationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishers[*].name", contains("Rio Grande Games")))
                .andExpect(jsonPath("$.categories[*].name", contains("Strategy")))
                .andExpect(jsonPath("$.mechanics[*].name", contains("Deck-building")))
                .andExpect(jsonPath("$.authors[*].lastName", contains("Knizia")));

        // submittedBy nie jest eksponowane w GameDto — weryfikacja przez repozytorium
        Game created = gameRepository.findByTitle("Terraforming Mars").getFirst();
        assertThat(created.getSubmittedBy()).isEqualTo(SeededUsers.JANE_ID);
        assertThat(created.getResubmissionCount()).isZero();
    }

    @Test
    @Transactional
    @DisplayName("POST /games submit=true -> 201 + PENDING")
    void createGame_submit_201() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moderationStatus").value("PENDING"));
    }

    @Test
    @DisplayName("POST /games bez tokena -> 401")
    void createGame_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false)))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /api/v1/games: walidacje ----------

    @Test
    @DisplayName("POST /games pusty tytuł -> 400 (Bean Validation)")
    void createGame_blankTitle_400() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false).replace("\"Terraforming Mars\"", "\"  \"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /games minPlayers > maxPlayers -> 400 (INVALID_PLAYER_COUNT)")
    void createGame_minGreaterThanMax_400() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false)
                                .replace("\"minPlayers\": 1", "\"minPlayers\": 6")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PLAYER_COUNT"));
    }

    @Test
    @DisplayName("POST /games bez żadnego wydawcy -> 400 (PUBLISHER_REQUIRED)")
    void createGame_noPublishers_400() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false).replace("\"publisherIds\": [1]", "\"publisherIds\": []")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PUBLISHER_REQUIRED"));
    }

    @Test
    @DisplayName("POST /games bez kategorii -> 400 (CATEGORY_REQUIRED)")
    void createGame_noCategories_400() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false).replace("\"categoryIds\": [1]", "\"categoryIds\": []")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_REQUIRED"));
    }

    @Test
    @DisplayName("POST /games nieistniejący publisherId -> 404 (PUBLISHER_NOT_FOUND)")
    void createGame_unknownPublisherId_404() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false).replace("\"publisherIds\": [1]", "\"publisherIds\": [99999]")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PUBLISHER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /games nieistniejący categoryId -> 404 (CATEGORY_NOT_FOUND)")
    void createGame_unknownCategoryId_404() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false).replace("\"categoryIds\": [1]", "\"categoryIds\": [99999]")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /games nieistniejący authorId -> 404 (AUTHOR_NOT_FOUND)")
    void createGame_unknownAuthorId_404() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false).replace("\"authorIds\": [2]", "\"authorIds\": [99999]")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("AUTHOR_NOT_FOUND"));
    }

    // ---------- POST /api/v1/games: wydawcy i autorzy w locie ----------

    @Test
    @Transactional
    @DisplayName("POST /games z nowym wydawcą (mieszany z istniejącym) -> 201, nowy jako PENDING")
    void createGame_newPublisherMixed_201() throws Exception {
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false).replace("\"newPublisherNames\": []",
                                "\"newPublisherNames\": [\"Wydawnictwo Nowe\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publishers", hasSize(2)))
                .andExpect(jsonPath("$.publishers[*].name",
                        containsInAnyOrder("Rio Grande Games", "Wydawnictwo Nowe")))
                .andExpect(jsonPath("$.publishers[?(@.name=='Wydawnictwo Nowe')].status", contains("PENDING")))
                .andExpect(jsonPath("$.publishers[?(@.name=='Rio Grande Games')].status", contains("APPROVED")));
    }

    @Test
    @Transactional
    @DisplayName("POST /games nowi autorzy (nowy=PENDING) + istniejący id; istniejąca para deduplikowana -> 201")
    void createGame_newAuthorsFindOrCreate_201() throws Exception {
        // newAuthors: (Uwe, Rosenberg) już istnieje jako autor 1 -> reuse (zostaje APPROVED);
        // (Nowa, Autorka) -> create jako PENDING. authorIds: [2] (Reiner Knizia). Razem dokładnie 3 autorów.
        mockMvc.perform(post("/api/v1/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false).replace("\"newAuthors\": []",
                                """
                                "newAuthors": [
                                  {"firstName": "Uwe", "lastName": "Rosenberg"},
                                  {"firstName": "Nowa", "lastName": "Autorka"}
                                ]""")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authors", hasSize(3)))
                .andExpect(jsonPath("$.authors[*].lastName",
                        containsInAnyOrder("Rosenberg", "Knizia", "Autorka")))
                // dedup: Rosenberg wskazuje na istniejącą encję (id 1), nie duplikat — i nie traci APPROVED
                .andExpect(jsonPath("$.authors[?(@.lastName=='Rosenberg')].id", contains(1)))
                .andExpect(jsonPath("$.authors[?(@.lastName=='Rosenberg')].status", contains("APPROVED")))
                .andExpect(jsonPath("$.authors[?(@.lastName=='Autorka')].status", contains("PENDING")));
    }

    // ---------- GET /api/v1/games/my ----------

    @Test
    @DisplayName("GET /games/my -> 200, tylko własne DRAFT/PENDING/REJECTED, bez APPROVED")
    void mySubmissions_returnsOwnNonApproved_200() throws Exception {
        mockMvc.perform(get("/api/v1/games/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].title",
                        containsInAnyOrder("Pandemic", "Szkic Jane", "Odrzucona Jane", "Limit Jane")))
                .andExpect(jsonPath("$.content[*].moderationStatus", not(hasItem("APPROVED"))));
    }

    @Test
    @DisplayName("GET /games/my z paginacją -> rozmiar strony respektowany")
    void mySubmissions_pagination_200() throws Exception {
        mockMvc.perform(get("/api/v1/games/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("GET /games/my bez tokena -> 401")
    void mySubmissions_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/games/my"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- GET /api/v1/games/{id} ----------

    @Test
    @DisplayName("GET /games/{id} własny DRAFT -> 200 + pełne DTO")
    void getGame_ownDraft_200() throws Exception {
        mockMvc.perform(get("/api/v1/games/4")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.title").value("Szkic Jane"))
                .andExpect(jsonPath("$.moderationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishers[*].name", contains("Rio Grande Games")));
    }

    @Test
    @DisplayName("GET /games/{id} własny APPROVED -> 200 (właściciel widzi własną grę w każdym statusie)")
    void getGame_ownApproved_200() throws Exception {
        mockMvc.perform(get("/api/v1/games/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Agricola"))
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /games/{id} cudze zgłoszenie -> 404 (GAME_NOT_FOUND, bez ujawniania istnienia)")
    void getGame_notOwner_404() throws Exception {
        // gra 3 należy do Johna; Jane dostaje odpowiedź nieodróżnialną od nieistniejącej gry
        mockMvc.perform(get("/api/v1/games/3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /games/{id} nieistniejący -> 404 (GAME_NOT_FOUND)")
    void getGame_notFound_404() throws Exception {
        mockMvc.perform(get("/api/v1/games/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /games/{id} bez tokena -> 401")
    void getGame_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/games/4"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- PUT /api/v1/games/{id} ----------

    @Test
    @Transactional
    @DisplayName("PUT /games/{id} własny DRAFT -> 200, pola i relacje podmienione, status bez zmian")
    void updateGame_ownDraft_200() throws Exception {
        mockMvc.perform(put("/api/v1/games/4")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false).replace("\"Terraforming Mars\"", "\"Szkic Jane v2\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.title").value("Szkic Jane v2"))
                .andExpect(jsonPath("$.moderationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.publishers[*].name", contains("Rio Grande Games")));
    }

    @Test
    @Transactional
    @DisplayName("PUT /games/{id} własny REJECTED -> 200, status pozostaje REJECTED")
    void updateGame_ownRejected_200() throws Exception {
        mockMvc.perform(put("/api/v1/games/5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false).replace("\"Terraforming Mars\"", "\"Odrzucona Jane v2\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Odrzucona Jane v2"))
                .andExpect(jsonPath("$.moderationStatus").value("REJECTED"));
    }

    @Test
    @DisplayName("PUT /games/{id} własny PENDING -> 409 (GAME_NOT_EDITABLE)")
    void updateGame_ownPending_409() throws Exception {
        mockMvc.perform(put("/api/v1/games/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("PUT /games/{id} cudze zgłoszenie -> 404 (GAME_NOT_FOUND, bez ujawniania istnienia)")
    void updateGame_notOwner_404() throws Exception {
        mockMvc.perform(put("/api/v1/games/3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /games/{id} nieistniejący -> 404 (GAME_NOT_FOUND)")
    void updateGame_notFound_404() throws Exception {
        mockMvc.perform(put("/api/v1/games/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    // ---------- POST /api/v1/games/{id}/submit ----------

    @Test
    @Transactional
    @DisplayName("POST /games/{id}/submit z DRAFT -> 200 + PENDING, count bez zmian")
    void submitGame_draft_200() throws Exception {
        mockMvc.perform(post("/api/v1/games/4/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("PENDING"));

        Game submitted = gameRepository.findById(4L).orElseThrow();
        assertThat(submitted.getResubmissionCount()).isZero();
    }

    @Test
    @Transactional
    @DisplayName("POST /games/{id}/submit z REJECTED -> 200 + PENDING, count+1, powód wyczyszczony")
    void submitGame_rejected_200_incrementsCount() throws Exception {
        mockMvc.perform(post("/api/v1/games/5/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationStatus").value("PENDING"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        Game resubmitted = gameRepository.findById(5L).orElseThrow();
        assertThat(resubmitted.getResubmissionCount()).isEqualTo(2);
        assertThat(resubmitted.getReviewedBy()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("POST /games/{id}/submit po wyczerpaniu limitu -> 409 (RESUBMISSION_LIMIT_EXCEEDED), status zostaje REJECTED")
    void submitGame_limitExceeded_409() throws Exception {
        // limit testowy = 2 (application-test.yml), gra 6 ma resubmission_count = 2
        mockMvc.perform(post("/api/v1/games/6/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESUBMISSION_LIMIT_EXCEEDED"));

        Game blocked = gameRepository.findById(6L).orElseThrow();
        assertThat(blocked.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(blocked.getResubmissionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("POST /games/{id}/submit z PENDING -> 409 (GAME_NOT_EDITABLE)")
    void submitGame_pending_409() throws Exception {
        mockMvc.perform(post("/api/v1/games/2/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("POST /games/{id}/submit cudze zgłoszenie -> 404 (GAME_NOT_FOUND)")
    void submitGame_notOwner_404() throws Exception {
        mockMvc.perform(post("/api/v1/games/3/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /games/{id}/submit bez tokena -> 401")
    void submitGame_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/v1/games/2/submit"))
                .andExpect(status().isUnauthorized());
    }
}