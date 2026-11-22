package pl.m22.gamehive.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pl.m22.gamehive.common.logging.CorrelationIdFilter.CORRELATION_ID;
import static pl.m22.gamehive.common.logging.CorrelationIdFilter.CORRELATION_ID_HEADER;

/**
 * GH-163: {@link CorrelationIdFilter} działa PRZED łańcuchem Spring Security ({@code @Order}), więc odpowiedzi
 * kończone przez Security (401 z entry pointu, 401 pisane przez {@link JwtAuthFilter}) niosą
 * {@code X-Correlation-Id}, a logi {@code JwtAuthFilter} mają {@code correlationId} w MDC.
 *
 * <p>MockMvc z {@code @AutoConfigureMockMvc} układa filtry w porządku rejestracji, więc ten test widzi kolejność.
 * ERROR dispatchu MockMvc nie wykonuje — ta część jest pokryta jednostkowo w {@code CorrelationIdFilterTest}
 * i sprawdzana na {@code dev}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationIdSecurityOrderTest {

    private static final String JANE = "jane.smith@example.com";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisSessionEpochStore sessionEpochStore;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired CacheManager cacheManager;
    @MockitoBean JavaMailSender mailSender;

    private final Logger jwtFilterLogger = (Logger) LoggerFactory.getLogger(JwtAuthFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        resetSecurityState();
        originalLevel = jwtFilterLogger.getLevel();
        jwtFilterLogger.setLevel(Level.DEBUG);
        appender.start();
        jwtFilterLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        jwtFilterLogger.detachAppender(appender);
        jwtFilterLogger.setLevel(originalLevel);
        // epoka sesji Jane nie może zostać dla innych klas testowych
        resetSecurityState();
    }

    @Test
    @DisplayName("401 ACCESS_DENIED z entry pointu Security (brak tokena) -> niesie wygenerowany X-Correlation-Id")
    void entryPoint401_carriesGeneratedCorrelationId() throws Exception {
        String header = mockMvc.perform(get("/api/v1/collection/games/ids"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andReturn().getResponse().getHeader(CORRELATION_ID_HEADER);

        assertThat(header).isNotNull();
        assertThat(UUID.fromString(header).toString()).isEqualTo(header);
    }

    @Test
    @DisplayName("401 ACCESS_DENIED -> poprawny X-Correlation-Id klienta odesłany bez zmian")
    void entryPoint401_echoesValidClientCorrelationId() throws Exception {
        String header = mockMvc.perform(get("/api/v1/collection/games/ids")
                        .header(CORRELATION_ID_HEADER, "client-corr-163"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getHeader(CORRELATION_ID_HEADER);

        assertThat(header).isEqualTo("client-corr-163");
    }

    @Test
    @DisplayName("401 TOKEN_REVOKED pisane przez JwtAuthFilter -> nagłówek obecny, a log filtra ma ten sam correlationId w MDC")
    void jwtFilter401_carriesCorrelationIdInHeaderAndLogMdc() throws Exception {
        String token = jwtService.generateToken(JANE, JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        // epoka sesji w ms, iat tokena ucięty do sekund: po >= 1 ms epoka jest na pewno > iat -> token zrewokowany
        Thread.sleep(2);
        sessionEpochStore.invalidateNow(JANE);

        String header = mockMvc.perform(get("/api/v1/collection/games/ids")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(CORRELATION_ID_HEADER, "revoked-corr-163"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"))
                .andReturn().getResponse().getHeader(CORRELATION_ID_HEADER);

        assertThat(header).isEqualTo("revoked-corr-163");

        List<ILoggingEvent> revokedLogs = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("has been revoked"))
                .toList();
        assertThat(revokedLogs).isNotEmpty();
        assertThat(revokedLogs).allSatisfy(e ->
                assertThat(e.getMDCPropertyMap()).containsEntry(CORRELATION_ID, "revoked-corr-163"));
    }

    private void resetSecurityState() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
        Cache cache = cacheManager.getCache("userAuthState");
        if (cache != null) {
            cache.clear();
        }
    }
}
