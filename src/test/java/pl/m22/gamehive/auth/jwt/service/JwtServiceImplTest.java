package pl.m22.gamehive.auth.jwt.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.TokenPairDto;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceImplTest {

    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;

    private static final String ACCESS_SECRET = "test-access-secret-abcdefghijklmnopqrstuvwxyz1234567";
    private static final String ACTIVATION_SECRET = "test-activation-secret-abcdefghijklmnopqrstuvwxyz1234";

    private CredentialsDto johnCreds() {
        Optional<AppUser> userOpt = userRepository.findByEmail("john.doe@example.com");
        if (userOpt.isEmpty()) {
            throw new IllegalStateException("Seed user john.doe@example.com not found (did schema/data.sql run?)");
        }
        return new CredentialsDto("john.doe@example.com", Set.of("ROLE_ADMIN","ROLE_USER"));
    }

    @Test
    @DisplayName("generateToken/access -> token się weryfikuje poprawnie")
    void generate_and_validate_access_token_ok() {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN","ROLE_USER"));
        jwtService.validateToken(token, JwtTokenType.ACCESS);
    }

    @Test
    @DisplayName("generateTokenPair/refresh -> JTI zapisany, a token refresh przechodzi weryfikację")
    void refresh_token_persisted_and_valid() {
        TokenPairDto pair = jwtService.generateTokenPair(johnCreds());
        jwtService.validateToken(pair.refreshToken(), JwtTokenType.REFRESH);
    }

    @Test
    @DisplayName("refresh token po revokeUsersTokens(email) -> JWT_INVALID_JTI")
    void refresh_token_revoked_fails() {
        TokenPairDto pair = jwtService.generateTokenPair(johnCreds());
        String refresh = pair.refreshToken();
        jwtService.revokeUsersTokens("john.doe@example.com");
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> jwtService.validateToken(refresh, JwtTokenType.REFRESH));
        assertEquals(ErrorCode.JWT_INVALID_JTI, ex.getErrorCode());
    }

    @Test
    @DisplayName("niepoprawny podpis -> JWT_INVALID_SIGNATURE")
    void invalid_signature() {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN"));
        // psujemy podpis – najprościej: dokładamy śmieci na końcu, parsuje się, ale weryfikacja podpisu padnie
        String broken = token + "AA";
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> jwtService.validateToken(broken, JwtTokenType.ACCESS));
        assertEquals(ErrorCode.JWT_INVALID_SIGNATURE, ex.getErrorCode());
    }

    @Test
    @DisplayName("refresh token sprawdzany jako ACCESS -> JWT_INVALID_TYPE")
    void invalid_type() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("jane.smith@example.com")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("type", "REFRESH") // <-- różni się od oczekiwanego ACCESS
                .build();

        // 2) podpisz SEKRETEM OD ACCESS (tak jak oczekuje walidator)
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(ACCESS_SECRET.getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
        String token = jwt.serialize();

        // 3) walidacja powinna polec na typie
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> jwtService.validateToken(token, JwtTokenType.ACCESS));
        assertEquals(ErrorCode.JWT_INVALID_TYPE, ex.getErrorCode());
    }

    @Test
    @DisplayName("wygasły activation token -> JWT_EXPIRED")
    void expired_activation_token() {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("john.doe@example.com")
                .issueTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(now.minusSeconds(1)))
                .claim("type", "ACTIVATION")
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(ACTIVATION_SECRET.getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }

        String token = jwt.serialize();
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> jwtService.validateToken(token, JwtTokenType.ACTIVATION));
        assertEquals(ErrorCode.JWT_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("wygasły access token -> JWT_EXPIRED")
    void expired_access_token() {
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("john.doe@example.com")
                .issueTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(now.minusSeconds(1))) // już PRZESZŁO
                .claim("type", "ACCESS") // ważne: przejdzie weryfikację typu
                .claim("roles", Set.of("ROLE_ADMIN")) // opcjonalnie
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(ACCESS_SECRET.getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }

        String token = jwt.serialize();
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> jwtService.validateToken(token, JwtTokenType.ACCESS));
        assertEquals(ErrorCode.JWT_EXPIRED, ex.getErrorCode());
    }
}
