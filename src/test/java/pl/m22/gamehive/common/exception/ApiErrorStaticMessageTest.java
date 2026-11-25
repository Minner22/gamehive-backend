package pl.m22.gamehive.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GH-149: {@code ApiError.message} jest zawsze {@code ErrorCode.defaultMessage} — żadne dane z żądania (e-mail,
 * username) ani komunikaty bibliotek (Nimbus) nie wracają do klienta. Szczegóły, które przed GH-149 szły
 * w odpowiedzi, zostają w logu {@link GlobalExceptionHandler}. Przypadki pochodzą z tabeli w treści issue.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorStaticMessageTest {

    private static final String RESET_USER = "gh149_reset@test.com";

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RedisSessionEpochStore sessionEpochStore;
    @Autowired UserRepository userRepository;
    @MockitoBean JavaMailSender mailSender;

    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(appender);
        userRepository.findByEmail(new Email(RESET_USER))
                .ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("Login nieznanym e-mailem -> 401 z defaultMessage; zamaskowany e-mail tylko w logu")
    void login_unknownEmail_staticMessage() throws Exception {
        login("nobody-gh149@test.com", "password123")
                .andExpect(status().isUnauthorized());
        expectStaticBody(lastResult, ErrorCode.INVALID_CREDENTIALS);

        assertThat(responseBody()).doesNotContain("@");
        assertLogged(Level.WARN, "Invalid credentials for: no", "@test.com");
        assertNotLogged("nobody-gh149@test.com");
    }

    @Test
    @DisplayName("Login złym hasłem -> ta sama odpowiedź co nieznany e-mail (defaultMessage)")
    void login_wrongPassword_staticMessage() throws Exception {
        login("john.doe@example.com", "wrongpassword")
                .andExpect(status().isUnauthorized());
        expectStaticBody(lastResult, ErrorCode.INVALID_CREDENTIALS);

        assertThat(responseBody()).doesNotContain("@");
    }

    @Test
    @DisplayName("Rejestracja na zajęty username -> 409 bez nazwy w odpowiedzi; nazwa tylko w logu")
    void register_duplicateUsername_staticMessage() throws Exception {
        register("john_doe", "gh149_dup_username@test.com")
                .andExpect(status().isConflict());
        expectStaticBody(lastResult, ErrorCode.USERNAME_ALREADY_EXISTS);

        assertThat(responseBody()).doesNotContain("john_doe");
        assertLogged(Level.INFO, "Username already exists: john_doe");
    }

    @Test
    @DisplayName("Rejestracja na zajęty e-mail -> 409 bez adresu w odpowiedzi; zamaskowany adres tylko w logu")
    void register_duplicateEmail_staticMessage() throws Exception {
        register("gh149_dup_email", "john.doe@example.com")
                .andExpect(status().isConflict());
        expectStaticBody(lastResult, ErrorCode.EMAIL_ALREADY_EXISTS);

        assertThat(responseBody()).doesNotContain("@");
        assertLogged(Level.INFO, "Email already exists: jo", "@example.com");
        assertNotLogged("john.doe@example.com");
    }

    @Test
    @DisplayName("/refresh z nieparsowalnym cookie -> 400 JWT_PARSE_ERROR bez komunikatu Nimbusa w odpowiedzi")
    void refresh_unparsableCookie_staticMessage() throws Exception {
        lastResult = mockMvc.perform(get("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", "not-a-jwt")))
                .andExpect(status().isBadRequest());
        expectStaticBody(lastResult, ErrorCode.JWT_PARSE_ERROR);

        assertThat(responseBody()).doesNotContain("Failed to parse JWT:");
        assertLogged(Level.WARN, "Failed to parse JWT: ");
    }

    // Przed GH-149 rozróżnienie "Password reset" vs "Activation" (poprawka GH-145) było widoczne w odpowiedzi;
    // teraz żyje tylko w logu — te dwa testy pilnują, że nie zginęło.

    @Test
    @DisplayName("Ponowne użycie linku resetu -> 401 JWT_BLACKLISTED z defaultMessage; log mówi o resecie hasła, nie aktywacji")
    void passwordReset_reusedLink_staticMessage_logNamesResetToken() throws Exception {
        registerUser();
        String link = jwtService.generateToken(RESET_USER, JwtTokenType.PASSWORD_RESET, null);
        confirmPasswordReset(link, "firstPassword1").andExpect(status().isOk());
        appender.list.clear();

        confirmPasswordReset(link, "secondPassword1").andExpect(status().isUnauthorized());
        expectStaticBody(lastResult, ErrorCode.JWT_BLACKLISTED);

        assertLogged(Level.WARN, "Password reset token has already been used");
        assertNotLogged("Activation");
    }

    @Test
    @DisplayName("Link resetu sprzed epoki -> 401 TOKEN_REVOKED z defaultMessage; log mówi o resecie hasła")
    void passwordReset_revokedLink_staticMessage_logNamesResetToken() throws Exception {
        registerUser();
        String link = jwtService.generateToken(RESET_USER, JwtTokenType.PASSWORD_RESET, null);
        // epoka resetu jest ucięta do pełnych sekund, a porównanie ostre — musi wypaść w późniejszej sekundzie niż iat
        Thread.sleep(1100);
        sessionEpochStore.invalidatePasswordResetNow(RESET_USER);

        confirmPasswordReset(link, "newPassword1").andExpect(status().isUnauthorized());
        expectStaticBody(lastResult, ErrorCode.TOKEN_REVOKED);

        assertLogged(Level.WARN, "Password reset token has been suspended");
    }

    // ---------- helpers ----------

    private ResultActions lastResult;

    private ResultActions login(String email, String password) throws Exception {
        lastResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
        return lastResult;
    }

    private ResultActions register(String username, String email) throws Exception {
        lastResult = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","email":"%s","password":"password123"}
                        """.formatted(username, email)));
        return lastResult;
    }

    private void registerUser() throws Exception {
        register("gh149_reset", RESET_USER).andExpect(status().isOk());
    }

    private ResultActions confirmPasswordReset(String token, String newPassword) throws Exception {
        lastResult = mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","newPassword":"%s"}
                        """.formatted(token, newPassword)));
        return lastResult;
    }

    private static void expectStaticBody(ResultActions result, ErrorCode code) throws Exception {
        result.andExpect(jsonPath("$.errorCode").value(code.name()))
                .andExpect(jsonPath("$.message").value(code.getDefaultMessage()));
    }

    private String responseBody() throws Exception {
        return lastResult.andReturn().getResponse().getContentAsString();
    }

    private void assertLogged(Level level, String... fragments) {
        assertThat(appender.list)
                .as("log GlobalExceptionHandler na poziomie %s zawierający %s", level, String.join(" … ", fragments))
                .anySatisfy(e -> {
                    assertThat(e.getLevel()).isEqualTo(level);
                    for (String fragment : fragments) {
                        assertThat(e.getFormattedMessage()).contains(fragment);
                    }
                });
    }

    private void assertNotLogged(String fragment) {
        assertThat(appender.list).noneSatisfy(e -> assertThat(e.getFormattedMessage()).contains(fragment));
    }
}
