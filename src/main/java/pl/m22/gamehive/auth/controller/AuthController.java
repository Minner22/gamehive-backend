package pl.m22.gamehive.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginDto;
import pl.m22.gamehive.auth.dto.TokenPairDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.service.AuthService;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.common.email.service.MailService;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.logging.LoggingUtils;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.service.UserService;

import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final MailService mailService;
    private final UserMapper userMapper;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegistrationDto registrationDto) {

        String activationToken = authService.registerAndGenerateActivationToken(registrationDto);
        log.info("User registered: {}", LoggingUtils.obfuscateEmail(registrationDto.email()));

        mailService.sendActivationEmail(registrationDto.email(), activationToken);
        log.info("Activation email sent to: {}", LoggingUtils.obfuscateEmail(registrationDto.email()));

        return ResponseEntity.ok("User registration successful. Please check your email to confirm your account.");
    }

    @GetMapping("/activate")
    public ResponseEntity<String> activateAccount(@RequestParam("token") String token) {
        jwtService.isTokenValid(token, JwtTokenType.ACTIVATION);
        String email = jwtService.extractEmailFromToken(token);
        authService.activateUser(email);
        log.info("User account activated: {}", LoggingUtils.obfuscateEmail(email));
        return ResponseEntity.ok("Account has been successfully activated.");
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginDto loginDto) {

        CredentialsDto userCredentials = authService.login(loginDto);
        log.info("User logged in: {}", LoggingUtils.obfuscateEmail(userCredentials.email()));
        return generateTokens(userCredentials);
    }

    @GetMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshAccessToken(@CookieValue("refreshToken") String refreshToken) {
        jwtService.isTokenValid(refreshToken, JwtTokenType.REFRESH);
        String email = jwtService.extractEmailFromToken(refreshToken);
        CredentialsDto userCredentials = userMapper.toCredentialsDto(
                userService.findUserByEmail(email)
                        .orElseThrow(() -> new ApplicationException(ErrorCode.EMAIL_NOT_FOUND, "Email not found: " + email)));
        log.info("User refreshed access token: {}", LoggingUtils.obfuscateEmail(userCredentials.email()));
        return generateTokens(userCredentials);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String accessTokenHeader) {
        String accessToken = accessTokenHeader.substring(7);
        jwtService.isTokenValid(accessToken, JwtTokenType.ACCESS);
        String subjectEmail = jwtService.extractEmailFromToken(accessToken);
        jwtService.revokeUsersTokens(subjectEmail);
        log.info("User logged out: {}", LoggingUtils.obfuscateEmail(subjectEmail));
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<Map<String, String>> generateTokens(CredentialsDto userCredentials) {
        TokenPairDto loginResponse = jwtService.generateTokenPair(userCredentials);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", loginResponse.refreshToken())
                .httpOnly(true)
                //.secure(true)
                .path("/api/v1/auth/refresh")
                .maxAge(Duration.ofDays(7))
                .sameSite("Strict")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(Map.of("accessToken", loginResponse.accessToken()));
    }
}
