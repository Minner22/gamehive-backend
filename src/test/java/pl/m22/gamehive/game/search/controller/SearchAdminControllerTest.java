package pl.m22.gamehive.game.search.controller;

import org.junit.jupiter.api.AfterEach;
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
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.search.dto.ContentReindexCounts;
import pl.m22.gamehive.game.search.dto.TaxonomyReindexCounts;
import pl.m22.gamehive.game.search.service.GameSearchService;
import pl.m22.gamehive.game.search.service.SearchReindexService;
import pl.m22.gamehive.game.search.service.TaxonomySuggestService;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @MockitoBean GameSearchService gameSearchService;
    @MockitoBean TaxonomySuggestService taxonomySuggestService;
    @MockitoBean JavaMailSender mailSender;

    private String moderatorToken;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        moderatorToken = jwtService.generateToken("mark.moderator@example.com", JwtTokenType.ACCESS, Set.of("ROLE_MODERATOR"));
        adminToken     = jwtService.generateToken("john.doe@example.com",       JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));
        userToken      = jwtService.generateToken("jane.smith@example.com",     JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        when(gameSearchService.reindexAll()).thenReturn(new ContentReindexCounts(2, 1));
        when(taxonomySuggestService.reindexAll()).thenReturn(new TaxonomyReindexCounts(3, 3));
    }

    // klucz blokady ma TTL 15 min, więc bez tego zostałby dla klas testowych, które nie robią flushAll
    @AfterEach
    void releaseLock() {
        redisTemplate.delete(SearchReindexService.REINDEX_LOCK_KEY);
    }

    @Test
    @DisplayName("POST /admin/search/reindex jako MODERATOR -> 200 z licznikami OBU indeksów, każdy wołany raz")
    void reindex_asModerator_200() throws Exception {
        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games").value(2))
                .andExpect(jsonPath("$.expansions").value(1))
                .andExpect(jsonPath("$.publishers").value(3))
                .andExpect(jsonPath("$.authors").value(3));

        verify(gameSearchService, times(1)).reindexAll();
        verify(taxonomySuggestService, times(1)).reindexAll();
    }

    @Test
    @DisplayName("POST /admin/search/reindex jako ADMIN -> 200")
    void reindex_asAdmin_200() throws Exception {
        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        verify(gameSearchService).reindexAll();
    }

    @Test
    @DisplayName("POST /admin/search/reindex jako USER -> 403 ACCESS_DENIED, indeks nietknięty")
    void reindex_asUser_403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(gameSearchService);
        verifyNoInteractions(taxonomySuggestService);
    }

    @Test
    @DisplayName("POST /admin/search/reindex bez tokena -> 401 (własny entry point, nie 403)")
    void reindex_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/search/reindex"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(gameSearchService);
        verifyNoInteractions(taxonomySuggestService);
    }

    @Test
    @DisplayName("reindeks przy zajętej blokadzie -> 409 REINDEX_ALREADY_RUNNING, drugi przebieg nie startuje")
    void reindex_whileAnotherRunIsInProgress_409() throws Exception {
        redisTemplate.opsForValue()
                .set(SearchReindexService.REINDEX_LOCK_KEY, "inny-przebieg", Duration.ofMinutes(15));

        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REINDEX_ALREADY_RUNNING"));

        verifyNoInteractions(gameSearchService);
        verifyNoInteractions(taxonomySuggestService);
    }

    @Test
    @DisplayName("po zakończonym reindeksie blokada jest zwolniona — kolejny przebieg wchodzi normalnie")
    void reindex_releasesLockForTheNextRun() throws Exception {
        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk());

        verify(gameSearchService, times(2)).reindexAll();
        verify(taxonomySuggestService, times(2)).reindexAll();
        assertThat(redisTemplate.hasKey(SearchReindexService.REINDEX_LOCK_KEY)).isFalse();
    }

    @Test
    @DisplayName("nieosiągalna wyszukiwarka -> 503 SEARCH_INDEX_UNAVAILABLE (ścieżka synchroniczna, więc błąd wychodzi po HTTP)")
    void reindex_searchEngineDown_503() throws Exception {
        when(gameSearchService.reindexAll())
                .thenThrow(new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE));

        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("SEARCH_INDEX_UNAVAILABLE"));

        // blokada nie może zostać po nieudanym przebiegu, inaczej naprawa byłaby zablokowana na 15 min
        assertThat(redisTemplate.hasKey(SearchReindexService.REINDEX_LOCK_KEY)).isFalse();
    }
}
