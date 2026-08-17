package pl.m22.gamehive.game.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.config.AsyncConfig;
import pl.m22.gamehive.game.search.event.SearchIndexListener;
import pl.m22.gamehive.game.search.event.TaxonomyIndexListener;
import pl.m22.gamehive.game.search.service.DatabaseTaxonomySuggestService;
import pl.m22.gamehive.game.search.service.GameSearchService;
import pl.m22.gamehive.game.search.service.NoOpGameSearchService;
import pl.m22.gamehive.game.search.service.TaxonomySuggestService;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jedyna klasa, która NIE podmienia beana wyszukiwarki — dowód, że w profilu test żyje realny fallback
 * i endpointy odpowiadają 200 bez uruchomionego Meili (którego nie da się osadzić w testach).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchFallbackTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired GameSearchService gameSearchService;
    @Autowired @Qualifier(AsyncConfig.SEARCH_INDEX_EXECUTOR) Executor searchIndexExecutor;
    @Autowired SearchIndexListener searchIndexListener;
    @Autowired TaxonomySuggestService taxonomySuggestService;
    @Autowired TaxonomyIndexListener taxonomyIndexListener;
    @MockitoBean JavaMailSender mailSender;

    private String janeToken;
    private String moderatorToken;

    @BeforeEach
    void setUp() {
        janeToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        moderatorToken = jwtService.generateToken("mark.moderator@example.com", JwtTokenType.ACCESS, Set.of("ROLE_MODERATOR"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("w profilu test aktywny jest NoOpGameSearchService (gamehive.search.enabled=false)")
    void testProfile_activatesFallbackImplementation() {
        assertThat(gameSearchService).isInstanceOf(NoOpGameSearchService.class);
    }

    @Test
    @DisplayName("w profilu test executor indeksu jest inline (SyncTaskExecutor) — asercje po zdarzeniu zostają deterministyczne, bez Awaitility")
    void testProfile_usesInlineSearchIndexExecutor() {
        assertThat(searchIndexExecutor).isInstanceOf(SyncTaskExecutor.class);
    }

    /** Sama adnotacja @Async nic nie daje bez proxy — a proxy powstaje niezależnie od tego, że executor jest tu inline. */
    @Test
    @DisplayName("listener indeksu jest opakowany w proxy AOP — bez tego @Async byłoby martwą adnotacją")
    void searchIndexListener_isProxied() {
        assertThat(AopUtils.isAopProxy(searchIndexListener)).isTrue();
    }

    @Test
    @DisplayName("GET /games/search na fallbacku -> 200 i pusta strona, nie 5xx")
    void search_onFallback_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/games/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("q", "agricola"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("POST /admin/search/reindex na fallbacku -> 200 i zerowe liczniki OBU indeksów (bez łączenia z Meili)")
    void reindex_onFallback_returnsZeroCounters() throws Exception {
        mockMvc.perform(post("/api/v1/admin/search/reindex")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games").value(0))
                .andExpect(jsonPath("$.expansions").value(0))
                .andExpect(jsonPath("$.publishers").value(0))
                .andExpect(jsonPath("$.authors").value(0));
    }

    @Test
    @DisplayName("w profilu test aktywny jest DatabaseTaxonomySuggestService — fallback taksonomii DZIAŁA, nie jest no-opem")
    void testProfile_activatesDatabaseTaxonomyFallback() {
        assertThat(taxonomySuggestService).isInstanceOf(DatabaseTaxonomySuggestService.class);
    }

    @Test
    @DisplayName("listener taksonomii jest opakowany w proxy AOP — bez tego @Async byłoby martwą adnotacją")
    void taxonomyIndexListener_isProxied() {
        assertThat(AopUtils.isAopProxy(taxonomyIndexListener)).isTrue();
    }

    @Test
    @DisplayName("GET /taxonomy/publishers/suggest na fallbacku -> 200 z realnymi trafieniami, nie pusto")
    void suggestPublishers_onFallback_returnsRealMatches() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/publishers/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("q", "z-man"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Z-Man Games"));
    }

    @Test
    @DisplayName("GET /taxonomy/authors/suggest na fallbacku -> 200 z realnymi trafieniami")
    void suggestAuthors_onFallback_returnsRealMatches() throws Exception {
        mockMvc.perform(get("/api/v1/taxonomy/authors/suggest")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + janeToken)
                        .param("q", "rosenberg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firstName").value("Uwe"));
    }
}
