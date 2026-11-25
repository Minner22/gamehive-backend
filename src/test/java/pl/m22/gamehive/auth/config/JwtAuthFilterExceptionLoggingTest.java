package pl.m22.gamehive.auth.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.TokenBlacklistServiceImpl;
import pl.m22.gamehive.auth.jwt.service.UserAuthState;
import pl.m22.gamehive.auth.jwt.service.UserAuthStateProvider;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.common.logging.LoggingUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * GH-165: {@link JwtAuthFilter} rozróżnia "zły token" od "awarii". Dawniej {@code catch (Exception e)} logował
 * wszystko na DEBUG, więc padnięta baza albo Redis zamieniały się w ciche 401 dla poprawnego tokena.
 * Teraz {@link ApplicationException} (zły/wygasły token) → DEBUG, każdy inny {@link RuntimeException} → ERROR
 * ze stack trace'em. Dla klienta bez zmian: łańcuch idzie dalej bez uwierzytelnienia → entry point → 401.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthFilterExceptionLoggingTest {

    private static final String TOKEN = "header.payload.signature";
    private static final String EMAIL = "jane.smith@example.com";
    private static final String URI = "/api/v1/collection/games/ids";

    @Mock JwtService jwtService;
    @Mock UserAuthStateProvider userAuthStateProvider;
    @Mock RedisTemplate<String, String> redisTemplate;

    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(JwtAuthFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level originalLevel;

    private JwtAuthFilter filter;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        // prawdziwy TokenBlacklistServiceImpl na zmockowanym RedisTemplate — żeby trzeci test sprawdził
        // współpracę filtra z obsługą awarii z commitu 1, a nie z mockiem, który "wie", co ma zwrócić
        filter = new JwtAuthFilter(jwtService, new TokenBlacklistServiceImpl(redisTemplate), new ObjectMapper(),
                userAuthStateProvider);
        chain = new MockFilterChain();

        when(jwtService.extractJtiFromToken(TOKEN)).thenReturn("jti-1");
        when(jwtService.extractEmailFromToken(TOKEN)).thenReturn(EMAIL);
        when(jwtService.extractIssuedAtFromToken(TOKEN)).thenReturn(Instant.now());
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(userAuthStateProvider.getAuthState(EMAIL)).thenReturn(
                new UserAuthState(true, List.of(new SimpleGrantedAuthority("ROLE_USER")), null));

        originalLevel = filterLogger.getLevel();
        filterLogger.setLevel(Level.DEBUG);
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
        filterLogger.setLevel(originalLevel);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Kontrola: poprawny token -> uwierzytelniony, brak logów błędów")
    void validToken_authenticates() throws Exception {
        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(EMAIL);
        assertThat(eventsAt(Level.ERROR)).isEmpty();
    }

    @Test
    @DisplayName("Zły/wygasły token (ApplicationException) -> DEBUG, brak ERROR, łańcuch dalej bez uwierzytelnienia")
    void invalidToken_logsDebugOnly() throws Exception {
        doThrow(new ApplicationException(ErrorCode.JWT_EXPIRED))
                .when(jwtService).validateToken(TOKEN, JwtTokenType.ACCESS);

        runFilter();

        assertNotAuthenticatedButChainContinued();
        assertThat(eventsAt(Level.ERROR)).isEmpty();
        assertThat(eventsAt(Level.DEBUG)).anySatisfy(e ->
                assertThat(e.getFormattedMessage()).contains("JWT validation failed"));
    }

    @Test
    @DisplayName("Awaria infrastruktury (np. baza w UserAuthStateProvider) -> ERROR ze stack trace'em, nadal 401 przez entry point")
    void unexpectedRuntimeException_logsErrorWithStackTrace() throws Exception {
        when(userAuthStateProvider.getAuthState(EMAIL)).thenThrow(new IllegalStateException("DB down"));

        runFilter();

        assertNotAuthenticatedButChainContinued();
        assertThat(eventsAt(Level.ERROR)).singleElement().satisfies(e -> {
            assertThat(e.getFormattedMessage()).contains("Unexpected error while authenticating").contains(URI);
            assertThat(e.getThrowableProxy()).isNotNull();
            assertThat(e.getThrowableProxy().getMessage()).isEqualTo("DB down");
        });
    }

    @Test
    @DisplayName("InfrastructureException z walidacji (np. błąd JOSE) -> ERROR, nie DEBUG: to nie jest 'zły token'")
    void infrastructureException_isNotTreatedAsInvalidToken() throws Exception {
        doThrow(new InfrastructureException(ErrorCode.JWT_SIGNING_ERROR, "jose"))
                .when(jwtService).validateToken(TOKEN, JwtTokenType.ACCESS);

        runFilter();

        assertNotAuthenticatedButChainContinued();
        assertThat(eventsAt(Level.ERROR)).hasSize(1);
    }

    @Test
    @DisplayName("Timeout Redis przy sprawdzaniu blacklisty -> fail-open: poprawny token UWIERZYTELNIONY (dawniej ciche 401)")
    void redisTimeoutOnBlacklist_failsOpenAndAuthenticates() throws Exception {
        when(redisTemplate.hasKey(anyString())).thenThrow(new QueryTimeoutException("Redis command timed out"));

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(eventsAt(Level.ERROR)).isEmpty();
    }

    // ---------- GH-150: żaden log filtra nie zawiera pełnego e-maila ----------

    @Test
    @DisplayName("GH-150: konto zablokowane -> 401 ACCOUNT_DISABLED, log DEBUG z ZAMASKOWANYM e-mailem")
    void disabledAccount_logsObfuscatedEmail() throws Exception {
        when(userAuthStateProvider.getAuthState(EMAIL)).thenReturn(new UserAuthState(false, List.of(), null));

        MockHttpServletResponse response = runFilter();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("ACCOUNT_DISABLED");
        assertDebugLogMasksEmail("is disabled");
    }

    @Test
    @DisplayName("GH-150: token sprzed epoki sesji -> 401 TOKEN_REVOKED, log DEBUG z ZAMASKOWANYM e-mailem")
    void revokedToken_logsObfuscatedEmail() throws Exception {
        Instant issuedAt = Instant.now().minusSeconds(60);
        when(jwtService.extractIssuedAtFromToken(TOKEN)).thenReturn(issuedAt);
        when(userAuthStateProvider.getAuthState(EMAIL)).thenReturn(
                new UserAuthState(true, List.of(new SimpleGrantedAuthority("ROLE_USER")), Instant.now().toEpochMilli()));

        MockHttpServletResponse response = runFilter();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("TOKEN_REVOKED");
        assertDebugLogMasksEmail("has been revoked");
    }

    private void assertDebugLogMasksEmail(String messageFragment) {
        assertThat(eventsAt(Level.DEBUG))
                .filteredOn(e -> e.getFormattedMessage().contains(messageFragment))
                .singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage())
                        .contains(LoggingUtils.obfuscateEmail(EMAIL))
                        .doesNotContain(EMAIL));
    }

    private MockHttpServletResponse runFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", URI);
        request.setServletPath(URI);
        request.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private void assertNotAuthenticatedButChainContinued() {
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("łańcuch musi iść dalej (entry point odpowie 401)").isNotNull();
    }

    private List<ILoggingEvent> eventsAt(Level level) {
        return appender.list.stream().filter(e -> e.getLevel() == level).toList();
    }
}
