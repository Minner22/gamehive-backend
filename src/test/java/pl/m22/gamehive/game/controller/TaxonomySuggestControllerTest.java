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
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.dto.PublisherDto;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.search.service.TaxonomySuggestService;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaxonomySuggestControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;

    // fallback podmieniony mockiem — sprawdzamy wiązanie parametrów, nie zachowanie podpowiedzi
    @MockitoBean TaxonomySuggestService taxonomySuggestService;
    @MockitoBean JavaMailSender mailSender;

    private String userToken;

    @BeforeEach
    void setUp() {
        userToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        when(taxonomySuggestService.suggestPublishers(any(), anyInt())).thenReturn(List.of());
        when(taxonomySuggestService.suggestAuthors(any(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("GET /taxonomy/publishers/suggest -> 200; fraza i limit trafiają do serwisu")
    void suggestPublishers_bindsQueryAndLimit_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "rio")
                        .param("limit", "5"))
                .andExpect(status().isOk());

        verify(taxonomySuggestService).suggestPublishers("rio", 5);
    }

    @Test
    @DisplayName("brak parametru limit -> domyślnie 10")
    void suggestPublishers_defaultLimitIsTen() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "rio"))
                .andExpect(status().isOk());

        verify(taxonomySuggestService).suggestPublishers("rio", 10);
    }

    @Test
    @DisplayName("brak frazy -> q=null idzie do serwisu (przeglądanie po samym limicie)")
    void suggestPublishers_withoutQuery_passesNull() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk());

        verify(taxonomySuggestService).suggestPublishers(null, 10);
    }

    @Test
    @DisplayName("limit=500 -> zaciśnięty do 50, odpowiedź 200 (nie 400 — @Max dałoby 500 przez brak handlera)")
    void suggestPublishers_clampsExcessiveLimit() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "x")
                        .param("limit", "500"))
                .andExpect(status().isOk());

        verify(taxonomySuggestService).suggestPublishers("x", 50);
    }

    @Test
    @DisplayName("limit=0 i limit ujemny -> zaciśnięte do 1 (PageRequest.of(0, 0) rzuciłby wyjątek)")
    void suggestPublishers_clampsNonPositiveLimit() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("limit", "0"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("limit", "-3"))
                .andExpect(status().isOk());

        verify(taxonomySuggestService, times(2)).suggestPublishers(null, 1);
    }

    @Test
    @DisplayName("limit=abc -> 400 VALIDATION_ERROR, serwis nietknięty")
    void suggestPublishers_nonNumericLimit_400() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verifyNoInteractions(taxonomySuggestService);
    }

    @Test
    @DisplayName("odpowiedź jest płaską listą DTO ze statusem, bez opakowania w Page")
    void suggestPublishers_returnsFlatListWithStatus() throws Exception {
        when(taxonomySuggestService.suggestPublishers("pend", 10))
                .thenReturn(List.of(new PublisherDto(3L, "Pending Games", TaxonomyStatus.PENDING)));

        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "pend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("Pending Games"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    @DisplayName("GET /taxonomy/authors/suggest -> 200; fraza trafia do serwisu autorów")
    void suggestAuthors_bindsQuery_200() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/authors/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "knizia")
                        .param("limit", "3"))
                .andExpect(status().isOk());

        verify(taxonomySuggestService).suggestAuthors("knizia", 3);
        verify(taxonomySuggestService, never()).suggestPublishers(any(), anyInt());
    }

    @Test
    @DisplayName("nieosiągalna wyszukiwarka -> 503 SEARCH_INDEX_UNAVAILABLE, bez wycieku frazy do ciała")
    void suggestPublishers_whenSearchUnavailable_503() throws Exception {
        when(taxonomySuggestService.suggestPublishers(any(), anyInt()))
                .thenThrow(new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE));

        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .param("q", "sekretna fraza"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("SEARCH_INDEX_UNAVAILABLE"))
                .andExpect(jsonPath("$.message", not(containsString("sekretna fraza"))));
    }

    @Test
    @DisplayName("GET /taxonomy/publishers/suggest bez tokena -> 401 ACCESS_DENIED, serwis nietknięty")
    void suggestPublishers_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest").param("q", "rio"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        verifyNoInteractions(taxonomySuggestService);
    }

    @Test
    @DisplayName("GET /taxonomy/authors/suggest bez tokena -> 401")
    void suggestAuthors_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/authors/suggest").param("q", "uwe"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(taxonomySuggestService);
    }

    @Test
    @DisplayName("/suggest nie przesłania listy — GET /taxonomy/publishers nadal zwraca słownik")
    void suggest_doesNotShadowList() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));

        verifyNoInteractions(taxonomySuggestService);
    }
}
