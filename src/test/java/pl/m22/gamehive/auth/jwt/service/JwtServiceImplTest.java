package pl.m22.gamehive.auth.jwt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
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

import java.util.Optional;
import java.util.Set;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceImplTest {

    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;

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
        Assertions.assertTrue(jwtService.isTokenValid(token, JwtTokenType.ACCESS));
    }

    @Test
    @DisplayName("generateTokenPair/refresh -> JTI zapisany, a token refresh przechodzi weryfikację")
    void refresh_token_persisted_and_valid() {
        TokenPairDto pair = jwtService.generateTokenPair(johnCreds());
        Assertions.assertTrue(jwtService.isTokenValid(pair.refreshToken(), JwtTokenType.REFRESH));
    }

    @Test
    @DisplayName("refresh token po revokeUsersTokens(email) -> JWT_INVALID_JTI")
    void refresh_token_revoked_fails() {
        TokenPairDto pair = jwtService.generateTokenPair(johnCreds());
        jwtService.revokeUsersTokens("john.doe@example.com");
        ApplicationException ex = Assertions.assertThrows(ApplicationException.class,
                () -> jwtService.isTokenValid(pair.refreshToken(), JwtTokenType.REFRESH));
        Assertions.assertEquals(ErrorCode.JWT_INVALID_JTI, ex.getErrorCode());
    }

    @Test
    @DisplayName("niepoprawny podpis -> JWT_INVALID_SIGNATURE")
    void invalid_signature() {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN"));
        // psujemy podpis – najprościej: dokładamy śmieci na końcu, parsuje się, ale weryfikacja podpisu padnie
        String broken = token + "AA";
        ApplicationException ex = Assertions.assertThrows(ApplicationException.class,
                () -> jwtService.isTokenValid(broken, JwtTokenType.ACCESS));
        Assertions.assertEquals(ErrorCode.JWT_INVALID_SIGNATURE, ex.getErrorCode());
    }

    @Test
    @DisplayName("refresh token sprawdzany jako ACCESS -> JWT_INVALID_TYPE")
    void invalid_type() {
        TokenPairDto pair = jwtService.generateTokenPair(johnCreds());
        ApplicationException ex = Assertions.assertThrows(ApplicationException.class,
                () -> jwtService.isTokenValid(pair.refreshToken(), JwtTokenType.ACCESS));
        Assertions.assertEquals(ErrorCode.JWT_INVALID_TYPE, ex.getErrorCode());
    }

    @Test
    @DisplayName("wygasły access token -> JWT_EXPIRED")
    void expired_access_token() throws InterruptedException {
        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN"));
        // TTL ustawiony w application-test.yml na 2 sekundy
        Thread.sleep(2500);
        ApplicationException ex = Assertions.assertThrows(ApplicationException.class,
                () -> jwtService.isTokenValid(token, JwtTokenType.ACCESS));
        Assertions.assertEquals(ErrorCode.JWT_EXPIRED, ex.getErrorCode());
    }
}
