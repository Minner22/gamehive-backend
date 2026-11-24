package pl.m22.gamehive.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.auth.dto.*;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.auth.jwt.service.TokenBlacklistService;
import pl.m22.gamehive.auth.service.AuthService;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.*;
import pl.m22.gamehive.common.logging.LoggingUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Rejestracja, aktywacja konta, logowanie, odświeżanie tokenów, wylogowanie oraz reset hasła. Endpointy publiczne (bez nagłówka Authorization).")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACTIVATION_LABEL = "Activation";
    private static final String PASSWORD_RESET_LABEL = "Password reset";

    private final AuthService authService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RedisSessionEpochStore sessionEpochStore;

    @Operation(
            summary = "Rejestracja nowego użytkownika",
            description = "Tworzy nowe (nieaktywne) konto i wysyła na podany adres e-mail wiadomość z linkiem aktywacyjnym. Konto wymaga aktywacji przed pierwszym logowaniem.")
    @ApiResponse(responseCode = "200", description = "Konto utworzone, wiadomość aktywacyjna zaplanowana do wysyłki")
    @ApiResponse(responseCode = "400", description = "Błąd walidacji danych wejściowych",
            content = @Content(schema = @Schema(implementation = ApiValidationError.class)))
    @ApiResponse(responseCode = "409", description = "Użytkownik o podanym e-mailu już istnieje (EMAIL_ALREADY_EXISTS)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/register")
    public ResponseEntity<MessageResponseDto> register(@Valid @RequestBody RegistrationDto registrationDto) {

        authService.register(registrationDto);
        log.info("User registered; activation email dispatch scheduled");

        return ResponseEntity.ok(new MessageResponseDto("User registration successful. Please check your email to confirm your account."));
    }

    @Operation(
            summary = "Aktywacja konta",
            description = "Aktywuje konto na podstawie jednorazowego tokenu aktywacyjnego (JWT) z linku wysłanego mailem. Po użyciu token jest unieważniany (blacklist).")
    @ApiResponse(responseCode = "200", description = "Konto aktywowane")
    @ApiResponse(responseCode = "401", description = "Token nieprawidłowy, wygasły, już użyty (JWT_BLACKLISTED) lub zastąpiony nowszym (TOKEN_REVOKED)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/activate")
    public ResponseEntity<MessageResponseDto> activateAccount(
            @Parameter(description = "Token aktywacyjny (JWT) z linku w wiadomości e-mail", required = true)
            @RequestParam("token") String token) {

        Email email = validateOneTimeToken(token,
                JwtTokenType.ACTIVATION,
                tokenBlacklistService::isBlacklisted,
                sessionEpochStore::getActivationInvalidAfter,
                ACTIVATION_LABEL);

        authService.activateUser(email);

        tokenBlacklistService.blacklistToken(token);

        log.info("User account activated: {}", email.obfuscated());

        return ResponseEntity.ok(new MessageResponseDto("Account has been successfully activated."));
    }

    @Operation(
            summary = "Logowanie użytkownika",
            description = "Uwierzytelnia użytkownika i zwraca token dostępowy w ciele odpowiedzi. Token odświeżający (refresh) jest ustawiany jako bezpieczne ciasteczko HttpOnly o ścieżce /api/v1/auth/refresh.")
    @ApiResponse(responseCode = "200", description = "Zalogowano; access token w body, refresh token w ciasteczku")
    @ApiResponse(responseCode = "400", description = "Błąd walidacji danych wejściowych",
            content = @Content(schema = @Schema(implementation = ApiValidationError.class)))
    @ApiResponse(responseCode = "401",
            description = "Nieprawidłowy e-mail lub hasło (INVALID_CREDENTIALS) — jednolita odpowiedź, bez rozróżniania istnienia konta",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403",
            description = "Konto nieaktywne — zwracane dopiero po poprawnym uwierzytelnieniu (USER_NOT_ACTIVATED)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponseDto> login(@Valid @RequestBody LoginDto loginDto) {

        CredentialsDto userCredentials = authService.login(loginDto);
        log.info("User logged in: {}", new Email(userCredentials.email()).obfuscated());

        return generateTokens(userCredentials);
    }

    @Operation(
            summary = "Odświeżenie tokenu dostępowego",
            description = "Na podstawie ważnego tokenu odświeżającego (ciasteczko refreshToken) wydaje nowy token dostępowy oraz rotuje token odświeżający poprzedni token odświeżający przestaje być ważny.")
    @ApiResponse(responseCode = "200", description = "Wydano nowy access token; nowy refresh token w ciasteczku")
    @ApiResponse(responseCode = "401",
            description = "Brak ciasteczka refreshToken (REFRESH_TOKEN_MISSING), token odświeżający nieprawidłowy, wygasły lub odwołany (np. wypchnięty limitem sesji albo po wylogowaniu), konto wyłączone (ACCOUNT_DISABLED) — wtedy wszystkie tokeny odświeżające konta zostają unieważnione, albo ponowne użycie już zrotowanego tokenu (REFRESH_TOKEN_REUSED) - wtedy wszystkie sesje użytkownika zostają unieważnione",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Usługa tokenów chwilowo niedostępna (REDIS_UNAVAILABLE)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/refresh")
    public ResponseEntity<AccessTokenResponseDto> refreshAccessToken(
            @Parameter(description = "Token odświeżający przekazywany automatycznie jako ciasteczko HttpOnly", required = true)
            @CookieValue("refreshToken") String refreshToken) {

        TokenPairDto tokens = jwtService.rotateRefreshToken(refreshToken,
                email -> authService.loadRefreshCredentials(new Email(email)));

        log.info("User refreshed access token: {}",
                LoggingUtils.obfuscateEmail(jwtService.extractEmailFromToken(tokens.refreshToken())));

        return tokenResponse(tokens);
    }

    @Operation(
            summary = "Wylogowanie użytkownika",
            description = "Unieważnia bieżący token dostępowy (blacklist) oraz wszystkie tokeny odświeżające użytkownika i czyści ciasteczko refreshToken.")
    @ApiResponse(responseCode = "200", description = "Wylogowano; tokeny unieważnione, ciasteczko wyczyszczone")
    @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Usługa tokenów chwilowo niedostępna (REDIS_UNAVAILABLE) — ciasteczko refreshToken mimo to wyczyszczone, ale tokeny mogły nie zostać unieważnione",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(description = "Nagłówek z tokenem dostępowym w formacie `Bearer <token>`", required = true)
            @RequestHeader("Authorization") String accessTokenHeader,
            HttpServletResponse response) {

        String accessToken = Objects.nonNull(accessTokenHeader) && accessTokenHeader.startsWith(BEARER_PREFIX) ? accessTokenHeader.substring(BEARER_PREFIX.length()) : accessTokenHeader;

        jwtService.validateToken(accessToken, JwtTokenType.ACCESS);

        response.addHeader(HttpHeaders.SET_COOKIE, clearedRefreshCookie().toString());

        InfrastructureException failure = null;
        try {
            tokenBlacklistService.blacklistTokenStrict(accessToken);
        }catch (InfrastructureException e) {
            failure = e;
        }

        String subjectEmail = jwtService.extractEmailFromToken(accessToken);
        try {
            jwtService.revokeUsersTokens(subjectEmail);
        } catch (InfrastructureException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            log.warn("Logout only partially applied for {} - Redis unavailable; refresh cookie cleared", LoggingUtils.obfuscateEmail(subjectEmail));
            throw failure;
        }

        log.info("User logged out: {}", LoggingUtils.obfuscateEmail(subjectEmail));
        return ResponseEntity.ok().build();
    }

    private ResponseCookie clearedRefreshCookie() {

        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth/refresh")
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }

    @Operation(
            summary = "Żądanie resetu hasła",
            description = "Jeśli konto o podanym adresie istnieje, wysyła wiadomość z linkiem do resetu hasła. Odpowiedź jest zawsze 200 (brak enumeracji użytkowników).")
    @ApiResponse(responseCode = "200", description = "Żądanie przyjęte (niezależnie od istnienia konta)")
    @ApiResponse(responseCode = "400", description = "Błąd walidacji danych wejściowych",
            content = @Content(schema = @Schema(implementation = ApiValidationError.class)))
    @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto requestDto) {

        Email email = new Email(requestDto.email());

        authService.requestPasswordReset(email);
        log.info("Password reset requested for email: {}", email.obfuscated());

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Potwierdzenie resetu hasła",
            description = "Ustawia nowe hasło na podstawie jednorazowego tokenu resetu (JWT). Po użyciu token jest unieważniany, a istniejące sesje użytkownika odwoływane. Każde nowe żądanie resetu oraz każdy udany reset unieważnia wcześniej wysłane linki.")
    @ApiResponse(responseCode = "200", description = "Hasło zmienione")
    @ApiResponse(responseCode = "400", description = "Błąd walidacji danych wejściowych",
            content = @Content(schema = @Schema(implementation = ApiValidationError.class)))
    @ApiResponse(responseCode = "401", description = "Token nieprawidłowy, wygasły, już użyty (JWT_BLACKLISTED) lub unieważniony nowszym linkiem albo udanym resetem (TOKEN_REVOKED)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503",
            description = "Usługa tokenów chwilowo niedostępna (REDIS_UNAVAILABLE) — hasło NIE zostało zmienione, spróbuj ponownie za chwilę",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmDto confirmDto) {

        String token = confirmDto.token();
        Email email = validateOneTimeToken(token,
                JwtTokenType.PASSWORD_RESET,
                tokenBlacklistService::isBlacklistedStrict,
                sessionEpochStore::getPasswordResetInvalidAfter,
                PASSWORD_RESET_LABEL);

        authService.confirmPasswordReset(email, confirmDto.newPassword());

        tokenBlacklistService.blacklistToken(token);
        log.info("Password reset confirmed for email: {}", email.obfuscated());

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Ponowne wysłanie wiadomości aktywacyjnej",
            description = "Jeśli konto istnieje i nie jest jeszcze aktywne, wysyła nowy link aktywacyjny (poprzedni token zostaje zastąpiony). Odpowiedź jest zawsze 200 (brak enumeracji użytkowników).")
    @ApiResponse(responseCode = "200", description = "Żądanie przyjęte (niezależnie od istnienia/stanu konta)")
    @ApiResponse(responseCode = "400", description = "Błąd walidacji danych wejściowych",
            content = @Content(schema = @Schema(implementation = ApiValidationError.class)))
    @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/activation/resend")
    public ResponseEntity<Void> resendActivationEmail(@Valid @RequestBody ResendActivationEmailDto dto) {

        Email email = new Email(dto.email());

        authService.resendActivationEmail(email);
        log.info("Activation email resend requested for: {}", email.obfuscated());

        return ResponseEntity.ok().build();
    }

    private ResponseEntity<AccessTokenResponseDto> generateTokens(CredentialsDto userCredentials) {

        return tokenResponse(jwtService.generateTokenPair(userCredentials));
    }

    private ResponseEntity<AccessTokenResponseDto> tokenResponse(TokenPairDto loginResponse) {

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", loginResponse.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth/refresh")
                .maxAge(Duration.ofDays(7))
                .sameSite("Strict")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new AccessTokenResponseDto(loginResponse.accessToken()));
    }

    private Email validateOneTimeToken(String token,
                                       JwtTokenType type,
                                       Predicate<String> blackListCheck,
                                       Function<String, Long> epochLookup,
                                       String tokenLabel) {

        jwtService.validateToken(token, type);

        if (blackListCheck.test(jwtService.extractJtiFromToken(token))) {
            throw new ApplicationException(ErrorCode.JWT_BLACKLISTED,
                    tokenLabel + " token has already been used or is invalid.");
        }

        Email email = new Email(jwtService.extractEmailFromToken(token));

        Instant issuedAt = jwtService.extractIssuedAtFromToken(token);
        Long invalidAfter = epochLookup.apply(email.value());

        if (invalidAfter != null && issuedAt != null && issuedAt.toEpochMilli() < invalidAfter) {
            throw new ApplicationException(ErrorCode.TOKEN_REVOKED,
                    tokenLabel + " token has been suspended and is no longer valid");
        }

        return email;
    }
}
