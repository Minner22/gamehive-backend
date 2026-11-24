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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.TokenPairDto;
import pl.m22.gamehive.auth.event.RefreshTokenReuseDetectedEvent;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.common.exception.BaseException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class JwtServiceImplTest {

    @Autowired JwtService jwtService;
    @Autowired ApplicationEvents events;
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
    @DisplayName("GH-146: refresh po revokeUsersTokens -> rotateRefreshToken daje JWT_INVALID_JTI i ZERO zdarzeń reuse")
    void refresh_token_revoked_fails() {

        String refresh = jwtService.generateToken(ROTATION_EMAIL, JwtTokenType.REFRESH, null);
        jwtService.revokeUsersTokens(ROTATION_EMAIL);

        assertErrorCode(() -> rotate(refresh), ErrorCode.JWT_INVALID_JTI);
        assertThat(events.stream(RefreshTokenReuseDetectedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("GH-146: validateToken(REFRESH) NIE sprawdza Redisa — zrotowany token przechodzi (dlatego /refresh idzie przez consume)")
    void validateToken_refresh_doesNotTouchRedis() {

        String refresh = jwtService.generateToken(ROTATION_EMAIL, JwtTokenType.REFRESH, null);
        rotate(refresh);

        assertThatCode(() -> jwtService.validateToken(refresh, JwtTokenType.REFRESH)).doesNotThrowAnyException();
    }

    // ---------- GH-146: rotateRefreshToken ----------

    // spoza data.sql: zdarzenie reuse wylogowuje użytkownika (refreshe + epoka) — nie może dotknąć seedowanych kont
    private static final String ROTATION_EMAIL = "rotation.jwt@test.com";

    @Test
    @DisplayName("GH-146: rotateRefreshToken aktywnego tokena -> email właściciela, bez zdarzeń")
    void consume_active_returnsEmail() {

        String refresh = jwtService.generateToken(ROTATION_EMAIL, JwtTokenType.REFRESH, null);

        TokenPairDto pair = rotate(refresh);
        assertThat(jwtService.extractEmailFromToken(pair.refreshToken())).isEqualTo(ROTATION_EMAIL);
        assertThat(jwtService.extractEmailFromToken(pair.accessToken())).isEqualTo(ROTATION_EMAIL);
        assertThat(pair.refreshToken()).isNotEqualTo(refresh);
        assertThat(events.stream(RefreshTokenReuseDetectedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("GH-146: drugi consume -> REFRESH_TOKEN_REUSED i dokładnie jedno zdarzenie z emailem")
    void consume_twice_reusedWithEvent() {

        String refresh = jwtService.generateToken(ROTATION_EMAIL, JwtTokenType.REFRESH, null);
        rotate(refresh);

        assertErrorCode(() -> rotate(refresh), ErrorCode.REFRESH_TOKEN_REUSED);
        assertThat(events.stream(RefreshTokenReuseDetectedEvent.class))
                .containsExactly(new RefreshTokenReuseDetectedEvent(ROTATION_EMAIL));
    }

    @Test
    @DisplayName("GH-146: zły podpis -> JWT_INVALID_SIGNATURE i token NIE jest konsumowany (walidacja przed consume)")
    void consume_badSignature_doesNotConsume() throws Exception {

        String real = jwtService.generateToken(ROTATION_EMAIL, JwtTokenType.REFRESH, null);
        // te same claims (ten sam JTI, zapisany w Redis), ale podpis innym sekretem
        JWTClaimsSet claims = SignedJWT.parse(real).getJWTClaimsSet();
        SignedJWT forged = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        forged.sign(new MACSigner(ACCESS_SECRET.getBytes(StandardCharsets.UTF_8)));

        assertErrorCode(() -> rotate(forged.serialize()), ErrorCode.JWT_INVALID_SIGNATURE);
        assertThat(jwtService.extractEmailFromToken(rotate(real).refreshToken())).isEqualTo(ROTATION_EMAIL);
        assertThat(events.stream(RefreshTokenReuseDetectedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("GH-146: access token zamiast refresha -> JWT_INVALID_TYPE")
    void consume_wrongType_rejected() {

        String access = jwtService.generateToken(ROTATION_EMAIL, JwtTokenType.ACCESS, Set.of("ROLE_USER"));

        assertErrorCode(() -> rotate(access), ErrorCode.JWT_INVALID_SIGNATURE, ErrorCode.JWT_INVALID_TYPE);
    }

    private TokenPairDto rotate(String refreshToken) {
        return jwtService.rotateRefreshToken(refreshToken, email -> new CredentialsDto(email, Set.of("ROLE_USER")));
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode... anyOf) {
        assertThatThrownBy(call)
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isIn((Object[]) anyOf);
    }

    @Test
    @DisplayName("niepoprawny podpis -> JWT_INVALID_SIGNATURE")
    void invalid_signature() {

        String token = jwtService.generateToken("john.doe@example.com", JwtTokenType.ACCESS, Set.of("ROLE_ADMIN"));
        // psujemy podpis – najprościej: dokładamy śmieci na końcu, parsuje się, ale weryfikacja podpisu padnie
        String broken = token + "AA";

        assertThatThrownBy(() -> jwtService.validateToken(broken, JwtTokenType.ACCESS))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.JWT_INVALID_SIGNATURE);

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

        assertThatThrownBy(() -> jwtService.validateToken(token, JwtTokenType.ACCESS))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.JWT_INVALID_TYPE);

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

        assertThatThrownBy(() -> jwtService.validateToken(token, JwtTokenType.ACTIVATION))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.JWT_EXPIRED);

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

        assertThatThrownBy(() -> jwtService.validateToken(token, JwtTokenType.ACCESS))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.JWT_EXPIRED);

    }

    @Test
    @DisplayName("token bez expirationTime -> JWT_EXPIRED (gałąź getExpirationTime() == null)")
    void missing_expiration_token() {

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("john.doe@example.com")
                .issueTime(new Date())
                .claim("type", "ACCESS")
                .claim("roles", Set.of("ROLE_ADMIN"))
                .build();                          // brak expirationTime

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(ACCESS_SECRET.getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }

        String token = jwt.serialize();

        assertThatThrownBy(() -> jwtService.validateToken(token, JwtTokenType.ACCESS))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.JWT_EXPIRED);

    }
}
