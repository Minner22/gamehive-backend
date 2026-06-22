package pl.m22.gamehive.auth.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.auth.jwt.service.TokenBlacklistService;
import pl.m22.gamehive.user.repository.UserRepository;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired TokenBlacklistService tokenBlacklistService;
    @Autowired RedisSessionEpochStore sessionEpochStore;
    @MockitoBean JavaMailSender mailSender;

    @AfterEach
    void cleanup() {
        userRepository.findByEmail("ctrl_register@test.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("ctrl_activate@test.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("ctrl_notactivated@test.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("ctrl_pwreset@test.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("ctrl_actepoch@test.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("ctrl_resend@test.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("ctrl_resend_active@test.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("ctrl_resend_stale@test.com").ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("POST /register happy path -> 200, JSON {message}, mail wysłany")
    void register_happy_path_200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ctrl_reg","email":"ctrl_register@test.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("POST /register puste pola -> 400")
    void register_blank_fields_400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","email":"","password":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register duplikat email -> 409")
    void register_duplicate_email_409() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"other","email":"john.doe@example.com","password":"password123"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /login happy path -> 200, accessToken w body, cookie refreshToken")
    void login_happy_path_200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"john.doe@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")));
    }

    @Test
    @DisplayName("POST /login złe hasło -> 401")
    void login_wrong_password_401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"john.doe@example.com","password":"wrongpassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /login nieistniejący e-mail -> 401 INVALID_CREDENTIALS (identycznie jak złe hasło)")
    void login_nonExisting_email_401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {"email":"nobody-enum@test.com","password":"password123"}
                                  """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /login nieaktywowane konto -> 403")
    void login_not_activated_403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ctrl_notact","email":"ctrl_notactivated@test.com","password":"password123"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ctrl_notactivated@test.com","password":"password123"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /activate poprawny token -> 200")
    void activate_valid_token_200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ctrl_act","email":"ctrl_activate@test.com","password":"password123"}
                                """))
                .andExpect(status().isOk());

        String token = jwtService.generateToken("ctrl_activate@test.com", JwtTokenType.ACTIVATION, null);

        mockMvc.perform(get("/api/v1/auth/activate")
                        .param("token", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /activate nieważny token -> 4xx")
    void activate_invalid_token_4xx() throws Exception {
        mockMvc.perform(get("/api/v1/auth/activate")
                        .param("token", "not.a.valid.jwt"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /refresh poprawny cookie refreshToken -> 200, nowy accessToken")
    void refresh_valid_cookie_200() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"john.doe@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        Assertions.assertNotNull(setCookie);
        String refreshToken = setCookie.split("refreshToken=")[1].split(";")[0];

        mockMvc.perform(get("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("GET /refresh bez cookie refreshToken -> 401 REFRESH_TOKEN_MISSING")
    void refresh_missing_cookie_401() throws Exception {

        mockMvc.perform(get("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("REFRESH_TOKEN_MISSING"));
    }

    @Test
    @DisplayName("POST /logout poprawny Bearer -> 200, cookie wyczyszczone (Max-Age=0)")
    void logout_valid_bearer_200() throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("POST /logout token bez prefiksu Bearer -> 200")
    void logout_token_without_bearer_prefix_200() throws Exception {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN", "ROLE_USER"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /activate token już użyty (blacklisted) -> 401")
    void activate_blacklisted_token_401() throws Exception {
        String token = jwtService.generateToken("ctrl_activate@test.com", JwtTokenType.ACTIVATION, null);
        tokenBlacklistService.blacklistToken(token);

        mockMvc.perform(get("/api/v1/auth/activate")
                        .param("token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /password-reset/request istniejący email -> 200")
    void requestPasswordReset_existingEmail_200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"john.doe@example.com"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /password-reset/request nieistniejący email -> 200 (brak enumeracji userów)")
    void requestPasswordReset_nonExistingEmail_200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@test.com"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /password-reset/request nieprawidłowy format email -> 400")
    void requestPasswordReset_invalidEmailFormat_400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"notanemail"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /password-reset/confirm poprawny token -> 200, hasło zmienione")
    void confirmPasswordReset_validToken_200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ctrl_pwreset","email":"ctrl_pwreset@test.com","password":"oldPassword1"}
                                """))
                .andExpect(status().isOk());

        String token = jwtService.generateToken("ctrl_pwreset@test.com", JwtTokenType.PASSWORD_RESET, null);

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"newPassword1"}
                                """.formatted(token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /password-reset/confirm token już użyty (blacklisted) -> 401")
    void confirmPasswordReset_blacklistedToken_401() throws Exception {
        String token = jwtService.generateToken("ctrl_pwreset@test.com", JwtTokenType.PASSWORD_RESET, null);
        tokenBlacklistService.blacklistToken(token);

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"newPassword1"}
                                """.formatted(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /password-reset/confirm nieważny token -> 4xx")
    void confirmPasswordReset_invalidToken_4xx() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"not.a.valid.jwt","newPassword":"newPassword1"}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /password-reset/confirm puste pola -> 400")
    void confirmPasswordReset_blankFields_400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"","newPassword":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /activate token sprzed epoki aktywacji -> 401 (zrewokowany)")
    void activate_tokenBeforeActivationEpoch_401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""                                                                                                                                                                                                  
                              {"username":"ctrl_actepoch","email":"ctrl_actepoch@test.com","password":"password123"}
                              """))
                .andExpect(status().isOk());

        String staleToken = jwtService.generateToken("ctrl_actepoch@test.com", JwtTokenType.ACTIVATION, null);

        // epoka musi trafić w PÓŹNIEJSZĄ pełną sekundę niż iat staleToken (iat jest ucinany do sekund),
        // inaczej iat == epoka i ostre porównanie (<) by nie odrzuciło tokenu
        Thread.sleep(1100);
        sessionEpochStore.invalidateActivationNow("ctrl_actepoch@test.com");

        mockMvc.perform(get("/api/v1/auth/activate").param("token", staleToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /activation/resend nieaktywne konto -> 200, świeży mail wysłany")
    void resendActivation_inactiveAccount_200_sendsMail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""                                                                                                                                                                                                  
                              {"username":"ctrl_resend","email":"ctrl_resend@test.com","password":"password123"}
                              """))
                .andExpect(status().isOk());   // 1. mail (rejestracja)

        mockMvc.perform(post("/api/v1/auth/activation/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""                                                                                                                                                                                                  
                              {"email":"ctrl_resend@test.com"}
                              """))
                .andExpect(status().isOk());

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));   // rejestracja + resend
    }

    @Test
    @DisplayName("POST /activation/resend aktywne konto -> 200, brak wysyłki")
    void resendActivation_activeAccount_200_noMail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""                                                                                                                                                                                                  
                              {"username":"ctrl_res_act","email":"ctrl_resend_active@test.com","password":"password123"}
                              """))
                .andExpect(status().isOk());

        String token = jwtService.generateToken("ctrl_resend_active@test.com", JwtTokenType.ACTIVATION, null);
        mockMvc.perform(get("/api/v1/auth/activate").param("token", token))
                .andExpect(status().isOk());

        clearInvocations(mailSender);   // pomiń mail z rejestracji

        mockMvc.perform(post("/api/v1/auth/activation/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""                                                                                                                                                                                                  
                              {"email":"ctrl_resend_active@test.com"}
                              """))
                .andExpect(status().isOk());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("POST /activation/resend nieistniejące konto -> 200, brak wysyłki (anty-enumeracja)")
    void resendActivation_nonExisting_200_noMail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/activation/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""                                                                                                                                                                                                  
                              {"email":"nobody@test.com"}
                              """))
                .andExpect(status().isOk());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("POST /activation/resend nieprawidłowy format email -> 400")
    void resendActivation_invalidEmail_400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/activation/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""                                                                                                                                                                                                  
                              {"email":"notanemail"}
                              """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Po resendzie stary token aktywacyjny -> 401, świeży token -> 200")
    void resendActivation_oldTokenRevoked_newTokenWorks() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""                                                                                                                                                                                                  
                              {"username":"ctrl_res_stale","email":"ctrl_resend_stale@test.com","password":"password123"}
                              """))
                .andExpect(status().isOk());

        String oldToken = jwtService.generateToken("ctrl_resend_stale@test.com", JwtTokenType.ACTIVATION, null);

        // gwarancja, że epoka resendu trafi w późniejszą pełną sekundę niż iat starego tokenu (iat ucinany do sekund)
        Thread.sleep(1100);

        mockMvc.perform(post("/api/v1/auth/activation/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""                                                                                                                                                                                                  
                              {"email":"ctrl_resend_stale@test.com"}
                              """))
                .andExpect(status().isOk());

        // stary link już nie działa
        mockMvc.perform(get("/api/v1/auth/activate").param("token", oldToken))
                .andExpect(status().isUnauthorized());

        // token wydany po resendzie (iat >= epoki) nadal aktywuje konto
        String newToken = jwtService.generateToken("ctrl_resend_stale@test.com", JwtTokenType.ACTIVATION, null);
        mockMvc.perform(get("/api/v1/auth/activate").param("token", newToken))
                .andExpect(status().isOk());
    }
}