package pl.m22.gamehive.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.m22.gamehive.auth.config.JwtAuthFilter;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH-163: {@link JwtAuthFilter} jest {@code @Component}-em i {@code OncePerRequestFilter}-em, więc Boot sam
 * zarejestrowałby go DRUGI raz jako filtr servletowy (LOWEST_PRECEDENCE, wszystkie dispatchery) — obok miejsca
 * w łańcuchu Security. {@code SecurityConfig} wyłącza tę rejestrację; filtr ma działać tylko w łańcuchu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthFilterRegistrationTest {

    private static final String JANE = "jane.smith@example.com";

    @Autowired ListableBeanFactory beanFactory;
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisSessionEpochStore sessionEpochStore;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired CacheManager cacheManager;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        resetSecurityState();
    }

    @AfterEach
    void tearDown() {
        resetSecurityState();
    }

    @Test
    @DisplayName("Kontener servletów nie dostaje JwtAuthFilter: jedyna jego rejestracja jest wyłączona")
    void jwtAuthFilter_isNotRegisteredAsServletFilter() {
        // Ta sama kolekcja, z której Boot rejestruje filtry w kontenerze — zawiera też filtry-beany
        // zaadaptowane automatycznie. Bez wyłączającego beana byłaby tu WŁĄCZONA rejestracja JwtAuthFilter.
        List<FilterRegistrationBean<?>> jwtRegistrations =
                new ServletContextInitializerBeans(beanFactory, ServletContextInitializer.class).stream()
                        .filter(FilterRegistrationBean.class::isInstance)
                        .<FilterRegistrationBean<?>>map(i -> (FilterRegistrationBean<?>) i)
                        .filter(r -> r.getFilter() instanceof JwtAuthFilter)
                        .toList();

        assertThat(jwtRegistrations).hasSize(1);
        assertThat(jwtRegistrations.getFirst().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Filtr nadal działa w łańcuchu Security: poprawny Bearer -> 200")
    void jwtAuthFilter_stillAuthenticatesInSecurityChain() throws Exception {
        String token = jwtService.generateToken(JANE, JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        mockMvc.perform(get("/api/v1/collection/games/ids")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Filtr nadal działa w łańcuchu Security: token sprzed epoki sesji -> 401 TOKEN_REVOKED")
    void jwtAuthFilter_stillRejectsRevokedTokenInSecurityChain() throws Exception {
        String token = jwtService.generateToken(JANE, JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        Thread.sleep(2);   // epoka w ms > iat ucięty do sekund
        sessionEpochStore.invalidateNow(JANE);

        mockMvc.perform(get("/api/v1/collection/games/ids")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"));
    }

    private void resetSecurityState() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        Cache cache = cacheManager.getCache("userAuthState");
        if (cache != null) {
            cache.clear();
        }
    }
}
