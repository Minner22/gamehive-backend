package pl.m22.gamehive.game.search;

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
import pl.m22.gamehive.game.search.service.GameSearchService;
import pl.m22.gamehive.game.search.service.NoOpGameSearchService;

import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @MockitoBean JavaMailSender mailSender;

    private String janeToken;

    @BeforeEach
    void setUp() {
        janeToken = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("w profilu test aktywny jest NoOpGameSearchService (gamehive.search.enabled=false)")
    void testProfile_activatesFallbackImplementation() {
        assertThat(gameSearchService).isInstanceOf(NoOpGameSearchService.class);
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
}
