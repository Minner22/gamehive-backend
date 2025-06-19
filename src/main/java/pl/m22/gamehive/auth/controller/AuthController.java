package pl.m22.gamehive.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginDto;
import pl.m22.gamehive.auth.dto.LoginResponseDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.service.AuthService;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.common.email.service.MailService;
import pl.m22.gamehive.common.exception.EmailNotFoundException;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.service.UserService;

import java.time.Duration;
import java.util.Map;

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

        authService.register(registrationDto);
        String activationToken = jwtService.generateToken(registrationDto.email(), JwtTokenType.ACTIVATION);
        mailService.sendActivationEmail(registrationDto.email(), activationToken);
        return ResponseEntity.ok("User registration successful. Please check your email to confirm your account.");
    }

    @GetMapping("/activate")
    public ResponseEntity<String> activateAccount(@RequestParam("token") String token) {
        String email = jwtService.validateToken(token, JwtTokenType.ACTIVATION);
        authService.activateUser(email);
        return ResponseEntity.ok("Account has been successfully activated.");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDto loginDto) {

        CredentialsDto userCredentials = authService.login(loginDto);
        return generateTokens(userCredentials);
    }

    @GetMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(@CookieValue("refreshToken") String refreshToken) {
        String email = jwtService.validateToken(refreshToken, JwtTokenType.REFRESH);
        CredentialsDto userCredentials = userMapper.toCredentialsDto(
                userService.findUserByEmail(email)
                        .orElseThrow(() -> new EmailNotFoundException(email)));
        return generateTokens(userCredentials);
    }

    private ResponseEntity<?> generateTokens(CredentialsDto userCredentials) {
        LoginResponseDto loginResponse = jwtService.login(userCredentials);

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
