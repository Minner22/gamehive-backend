package pl.m22.gamehive.collection.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.collection.repository.ExpansionCollectionItemRepository;
import pl.m22.gamehive.collection.repository.GameCollectionItemRepository;
import pl.m22.gamehive.support.SeededUsers;

import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CollectionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired GameCollectionItemRepository gameCollectionRepository;
    @Autowired ExpansionCollectionItemRepository expansionCollectionRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired EntityManager em;
    @MockitoBean JavaMailSender mailSender;

    // Jane: gra 1 (Agricola) + dodatek 1 (Rzeka). John: gra 7 (Carcassonne). Mark: PUSTA kolekcja.
    private String janeToken;
    private String johnToken;
    private String markToken;

    @BeforeEach
    void setUp() {
        janeToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        johnToken = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        markToken = jwtService.generateToken("mark.moderator@example.com", JwtTokenType.ACCESS, Set.of("ROLE_MODERATOR"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    // ---------- GET /api/v1/collection/games ----------

    @Test
    @DisplayName("GET /collection/games -> 200, tylko własne wpisy z pełnymi danymi gry")
    void myGames_returnsOwnItems_200() throws Exception {
        mockMvc.perform(get("/api/v1/collection/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].game.title", contains("Agricola")))
                .andExpect(jsonPath("$.content[0].ownershipStatus").value("OWNED"))
                .andExpect(jsonPath("$.content[0].addedAt").exists())
                // dane celu pochodzą z GameMapper — pełne DTO gry, nie samo id
                .andExpect(jsonPath("$.content[0].game.publishers").isArray())
                .andExpect(jsonPath("$.content[0].game.moderationStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /collection/games -> kolekcja Johna nie zawiera wpisów Jane (izolacja)")
    void myGames_isolatedBetweenUsers_200() throws Exception {
        mockMvc.perform(get("/api/v1/collection/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].game.title", contains("Carcassonne")));
    }

    @Test
    @DisplayName("GET /collection/games dla użytkownika bez wpisów -> 200 i pusta strona")
    void myGames_empty_200() throws Exception {
        mockMvc.perform(get("/api/v1/collection/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + markToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.empty").value(true));
    }

    @Test
    @DisplayName("GET /collection/games bez tokena -> 401")
    void myGames_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/collection/games"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- POST /api/v1/collection/games/{gameId} ----------

    @Test
    @Transactional
    @DisplayName("POST /collection/games/{id} gry APPROVED -> 201, wpis OWNED przypisany do zalogowanego")
    void addGame_approved_201() throws Exception {
        mockMvc.perform(post("/api/v1/collection/games/7")     // Carcassonne (APPROVED, w kolekcji Johna)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.ownershipStatus").value("OWNED"))
                .andExpect(jsonPath("$.game.id").value(7))
                .andExpect(jsonPath("$.game.title").value("Carcassonne"));

        // ten sam cel u dwóch użytkowników jest dozwolony — unikat obejmuje parę
        assertThat(gameCollectionRepository.existsByUserIdAndGameId(SeededUsers.JANE_ID, 7L)).isTrue();
        assertThat(gameCollectionRepository.existsByUserIdAndGameId(SeededUsers.JOHN_ID, 7L)).isTrue();
    }

    @Test
    @DisplayName("POST /collection/games/{id} duplikat -> 409 ALREADY_IN_COLLECTION")
    void addGame_duplicate_409() throws Exception {
        mockMvc.perform(post("/api/v1/collection/games/1")     // Jane ma już Agricolę
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_IN_COLLECTION"));
    }

    @Test
    @DisplayName("POST /collection/games/{id} gry PENDING -> 409 GAME_NOT_APPROVED")
    void addGame_notApproved_409() throws Exception {
        mockMvc.perform(post("/api/v1/collection/games/2")     // Pandemic (PENDING)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_APPROVED"));
    }

    @Test
    @DisplayName("POST /collection/games/{id} własnego DRAFT-u -> 409 GAME_NOT_APPROVED (kolekcja to tylko biblioteka)")
    void addGame_ownDraft_409() throws Exception {
        mockMvc.perform(post("/api/v1/collection/games/4")     // Szkic Jane (DRAFT, jej własny)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_APPROVED"));
    }

    @Test
    @DisplayName("POST /collection/games/{id} nieistniejącej gry -> 404 GAME_NOT_FOUND")
    void addGame_notFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/collection/games/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /collection/games/{id} bez tokena -> 401")
    void addGame_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/v1/collection/games/7"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- DELETE /api/v1/collection/games/{gameId} ----------

    @Test
    @Transactional
    @DisplayName("DELETE /collection/games/{id} własnego wpisu -> 204, gra w bibliotece nietknięta")
    void removeGame_own_204() throws Exception {
        mockMvc.perform(delete("/api/v1/collection/games/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNoContent());

        assertThat(gameCollectionRepository.findByUserId(SeededUsers.JANE_ID, Pageable.unpaged())).isEmpty();

        // gra dalej jest w bibliotece
        mockMvc.perform(get("/api/v1/games/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /collection/games/{id} cudzego wpisu -> 404 COLLECTION_ITEM_NOT_FOUND, wpis zostaje")
    void removeGame_othersItem_404() throws Exception {
        // gra 7 jest w kolekcji Johna, nie Jane — z jej perspektywy nieodróżnialna od nieistniejącej
        mockMvc.perform(delete("/api/v1/collection/games/7")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COLLECTION_ITEM_NOT_FOUND"));

        assertThat(gameCollectionRepository.existsByUserIdAndGameId(SeededUsers.JOHN_ID, 7L)).isTrue();
    }

    @Test
    @DisplayName("DELETE /collection/games/{id} nieistniejącej gry -> 404 COLLECTION_ITEM_NOT_FOUND")
    void removeGame_notFound_404() throws Exception {
        mockMvc.perform(delete("/api/v1/collection/games/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COLLECTION_ITEM_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /collection/games/{id} bez tokena -> 401")
    void removeGame_unauthenticated_401() throws Exception {
        mockMvc.perform(delete("/api/v1/collection/games/1"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- dodatki ----------

    @Test
    @DisplayName("GET /collection/expansions -> 200, własny dodatek z wartościami efektywnymi")
    void myExpansions_returnsOwnItems_200() throws Exception {
        mockMvc.perform(get("/api/v1/collection/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].expansion.name", contains("Carcassonne: Rzeka")))
                .andExpect(jsonPath("$.content[0].ownershipStatus").value("OWNED"))
                // DTO celu to pełne GameExpansionDto — front widzi nadpisania i dziedziczenie
                .andExpect(jsonPath("$.content[0].expansion.maxPlayers").value(6))
                .andExpect(jsonPath("$.content[0].expansion.effectiveMinPlayers").value(2))
                .andExpect(jsonPath("$.content[0].expansion.baseGameTitle").value("Carcassonne"));
    }

    @Test
    @DisplayName("GET /collection/expansions dla użytkownika bez wpisów -> 200 i pusta strona")
    void myExpansions_empty_200() throws Exception {
        mockMvc.perform(get("/api/v1/collection/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + markToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @Transactional
    @DisplayName("POST /collection/expansions/{id} -> 201 niezależnie od tego, czy gra bazowa jest w kolekcji")
    void addExpansion_independentOfBaseGame_201() throws Exception {
        // Mark ma pustą kolekcję — dodaje dodatek 1, NIE mając w kolekcji jego gry bazowej (7)
        mockMvc.perform(post("/api/v1/collection/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + markToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownershipStatus").value("OWNED"))
                .andExpect(jsonPath("$.expansion.id").value(1));

        assertThat(expansionCollectionRepository.existsByUserIdAndExpansionId(SeededUsers.MARK_ID, 1L)).isTrue();
        assertThat(gameCollectionRepository.existsByUserIdAndGameId(SeededUsers.MARK_ID, 7L)).isFalse();
    }

    @Test
    @DisplayName("POST /collection/expansions/{id} duplikat -> 409 ALREADY_IN_COLLECTION")
    void addExpansion_duplicate_409() throws Exception {
        mockMvc.perform(post("/api/v1/collection/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_IN_COLLECTION"));
    }

    @Test
    @DisplayName("POST /collection/expansions/{id} dodatku PENDING -> 409 EXPANSION_NOT_APPROVED")
    void addExpansion_notApproved_409() throws Exception {
        mockMvc.perform(post("/api/v1/collection/expansions/2")   // Carcassonne: Karczmy (PENDING)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_APPROVED"));
    }

    @Test
    @DisplayName("POST /collection/expansions/{id} nieistniejącego -> 404 EXPANSION_NOT_FOUND")
    void addExpansion_notFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/collection/expansions/99999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EXPANSION_NOT_FOUND"));
    }

    @Test
    @Transactional
    @DisplayName("DELETE /collection/expansions/{id} własnego wpisu -> 204, dodatek w bibliotece zostaje")
    void removeExpansion_own_204() throws Exception {
        mockMvc.perform(delete("/api/v1/collection/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isNoContent());

        assertThat(expansionCollectionRepository.findByUserId(SeededUsers.JANE_ID, Pageable.unpaged())).isEmpty();

        mockMvc.perform(get("/api/v1/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /collection/expansions/{id} cudzego wpisu -> 404 COLLECTION_ITEM_NOT_FOUND")
    void removeExpansion_othersItem_404() throws Exception {
        mockMvc.perform(delete("/api/v1/collection/expansions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + markToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COLLECTION_ITEM_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /collection/expansions bez tokena -> 401")
    void myExpansions_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/collection/expansions"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- kaskada end-to-end (kryterium akceptacji #121) ----------

    @Test
    @Transactional
    @DisplayName("hard-delete gry przez moderatora -> wpis kolekcji znika (kaskada FK)")
    void moderatorHardDeleteGame_removesCollectionItem() throws Exception {
        mockMvc.perform(delete("/api/v1/moderation/games/1")     // Agricola, w kolekcji Jane
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + markToken))
                .andExpect(status().isNoContent());

        // test i endpoint dzielą transakcję: auto-flush obejmuje tylko przestrzeń tabel odpytywanego
        // zapytania, więc DELETE gry trzeba wypchnąć jawnie, żeby baza wykonała kaskadę
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/v1/collection/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @Transactional
    @DisplayName("hard-delete dodatku przez moderatora -> wpis kolekcji znika (kaskada FK)")
    void moderatorHardDeleteExpansion_removesCollectionItem() throws Exception {
        mockMvc.perform(delete("/api/v1/moderation/expansions/1")   // Rzeka, w kolekcji Jane
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + markToken))
                .andExpect(status().isNoContent());

        em.flush();
        em.clear();

        mockMvc.perform(get("/api/v1/collection/expansions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
