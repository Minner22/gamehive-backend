package pl.m22.gamehive.game.search.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.dto.GameSearchFilter;
import pl.m22.gamehive.game.search.service.GameSearchService;

import java.util.Objects;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameSearchControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;

    // fallback podmieniony mockiem — sprawdzamy wiązanie parametrów, nie zachowanie wyszukiwarki
    @MockitoBean GameSearchService gameSearchService;
    @MockitoBean JavaMailSender mailSender;

    private String janeToken;

    @BeforeEach
    void setUp() {
        janeToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        when(gameSearchService.search(any(), any(), any())).thenReturn(Page.empty(PageRequest.of(0, 20)));
    }

    @Test
    @DisplayName("GET /games/search bez tokena -> 401 (cała faza pod JWT)")
    void search_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/games/search").param("q", "agricola"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /games/search -> 200; fraza i wszystkie filtry trafiają do GameSearchFilter, strona 0/20")
    void search_bindsQueryAndAllFilters_200() throws Exception {
        mockMvc.perform(get("/api/v1/games/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("q", "carcassonne")
                        .param("targetType", "EXPANSION")
                        .param("publisherId", "2")
                        .param("categoryId", "5")
                        .param("mechanicId", "3")
                        .param("authorId", "1")
                        .param("baseGameId", "7")
                        .param("players", "3")
                        .param("maxPlayingTime", "60")
                        .param("yearPublished", "2000")
                        .param("age", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(gameSearchService).search(
                eq("carcassonne"),
                eq(new GameSearchFilter(ContentModerationTargetType.EXPANSION, 2L, 5L, 3L, 1L, 7L, 3, 60, 2000, 10)),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 20));
    }

    @Test
    @DisplayName("GET /games/search bez parametrów -> pusty filtr i brak frazy (przeglądanie po samym rankingu)")
    void search_withoutParams_passesEmptyFilter() throws Exception {
        mockMvc.perform(get("/api/v1/games/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk());

        verify(gameSearchService).search(
                isNull(),
                eq(new GameSearchFilter(null, null, null, null, null, null, null, null, null, null)),
                any());
    }

    @Test
    @DisplayName("GET /games/search?page=2&size=5 -> paginacja przekazana do serwisu")
    void search_honoursPagination() throws Exception {
        mockMvc.perform(get("/api/v1/games/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("q", "x")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(gameSearchService).search(eq("x"), any(),
                argThat(pageable -> pageable.getPageNumber() == 2 && pageable.getPageSize() == 5));
    }

    @Test
    @DisplayName("GET /games/search?targetType=FOO -> 400 VALIDATION_ERROR, wyszukiwarka nietknięta")
    void search_invalidTargetType_400() throws Exception {
        mockMvc.perform(get("/api/v1/games/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("targetType", "FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /games/search?players=abc -> 400 VALIDATION_ERROR (zły typ filtra liczbowego)")
    void search_invalidNumericFilter_400() throws Exception {
        mockMvc.perform(get("/api/v1/games/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("players", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /games/search/{id} nie istnieje — /search nie koliduje z GET /games/{id}")
    void search_doesNotShadowGameById() throws Exception {
        mockMvc.perform(get("/api/v1/games/7")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Carcassonne"));
    }
}
