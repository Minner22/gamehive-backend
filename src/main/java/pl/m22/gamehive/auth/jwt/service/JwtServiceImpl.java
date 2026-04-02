package pl.m22.gamehive.auth.jwt.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.TokenPairDto;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.config.AccessTokenProperties;
import pl.m22.gamehive.auth.jwt.config.ActivationTokenProperties;
import pl.m22.gamehive.auth.jwt.config.PasswordResetTokenProperties;
import pl.m22.gamehive.auth.jwt.config.RefreshTokenProperties;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private static final JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;
    private final ActivationTokenProperties activationProps;
    private final AccessTokenProperties accessProps;
    private final RefreshTokenProperties refreshProps;
    private final PasswordResetTokenProperties passwordResetProps;
    private final RedisRefreshTokenStore redisRefreshTokenStore;
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLES = "roles";

    @Override
    public void validateToken(String token, JwtTokenType tokenType) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.getHeader().getAlgorithm().equals(jwsAlgorithm)) {
                throw new ApplicationException(ErrorCode.JWT_INVALID_ALGORITHM,
                        "Invalid JWT algorithm: " + signedJWT.getHeader().getAlgorithm().getName());
            }
            if (!signedJWT.verify(new MACVerifier(getSecretForType(tokenType)))) {
                throw new ApplicationException(ErrorCode.JWT_INVALID_SIGNATURE);
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                throw new ApplicationException(ErrorCode.JWT_EXPIRED);
            }

            String type = claims.getStringClaim(CLAIM_TYPE);
            if (!tokenType.name().equals(type)) {
                throw new ApplicationException(
                        ErrorCode.JWT_INVALID_TYPE,
                        "Expected token type " + tokenType + " but got " + type
                );
            }

            if (tokenType == JwtTokenType.REFRESH) {
                String jti = claims.getJWTID();
                if (jti == null || jti.isEmpty()) {
                    throw new ApplicationException(ErrorCode.JWT_INVALID_JTI);
                }
                if (!redisRefreshTokenStore.existsByJti(jti)) {
                    throw new ApplicationException(ErrorCode.JWT_INVALID_JTI);
                }
            }

        } catch (ParseException e) {
            throw new ApplicationException(
                    ErrorCode.JWT_PARSE_ERROR,
                    "Failed to parse JWT: " + e.getMessage()
            );
        } catch (JOSEException e) {
            throw new InfrastructureException(
                    ErrorCode.JWT_SIGNING_ERROR,
                    "Failed to sign JWT: " + e.getMessage()
            );
        }
    }

    @Override
    public TokenPairDto generateTokenPair(CredentialsDto credentials) {
        String accessToken = generateToken(credentials.email(), JwtTokenType.ACCESS, credentials.roles());
        String refreshToken = generateToken(credentials.email(), JwtTokenType.REFRESH, null);

        return new TokenPairDto(accessToken, refreshToken);
    }

    @Override
    public String generateToken(String subjectEmail, JwtTokenType tokenType, Set<String> roles) {
        JWTClaimsSet claimsSet = createPayload(subjectEmail, tokenType, roles);
        SignedJWT signedJwt = generateSignedJwt(claimsSet, tokenType);

        if (tokenType == JwtTokenType.REFRESH) {
            saveRefreshToken(claimsSet);
        }

        return signedJwt.serialize();
    }

    @Override
    public String extractEmailFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
            if (jwtClaimsSet.getSubject() == null || jwtClaimsSet.getSubject().isEmpty()) {
                throw new ApplicationException(ErrorCode.JWT_INVALID_SUBJECT);
            }
            return jwtClaimsSet.getSubject();
        } catch (ParseException e) {
            throw new ApplicationException(
                    ErrorCode.JWT_PARSE_ERROR,
                    "Failed to parse JWT: " + e.getMessage()
            );
        }
    }

    @Override
    public String extractJtiFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getJWTID();
        } catch (ParseException e) {
            throw new ApplicationException(
                    ErrorCode.JWT_PARSE_ERROR,
                    "Failed to parse JWT: " + e.getMessage()
            );
        }
    }

    @Override
    public void revokeUsersTokens(String email) {
        if (email == null || email.isEmpty()) {
            throw new ApplicationException(ErrorCode.EMAIL_NOT_FOUND, "Email cannot be null or empty");
        }

        redisRefreshTokenStore.revokeAllByUserEmail(email);
    }

    private SignedJWT generateSignedJwt(JWTClaimsSet claimSet, JwtTokenType tokenType) {
        JWSHeader header = new JWSHeader.Builder(jwsAlgorithm)
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claimSet);
        try {
            JWSSigner jwsSigner = new MACSigner(getSecretForType(tokenType));
            signedJWT.sign(jwsSigner);
            return signedJWT;
        } catch (KeyLengthException e) {
            throw new InfrastructureException(
                    ErrorCode.JWT_KEY_ERROR,
                    "Invalid key length for JWT signing: " + e.getMessage()
            );
        } catch (JOSEException e) {
            throw new InfrastructureException(
                    ErrorCode.JWT_SIGNING_ERROR,
                    "Failed to sign JWT: " + e.getMessage()
            );
        }
    }

    private JWTClaimsSet createPayload(String subjectEmail, JwtTokenType tokenType, Set<String> roles) {
        Instant now = Instant.now();
        Instant expirationDate = now.plusSeconds(getValidityForType(tokenType));

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(subjectEmail)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expirationDate))
                .claim(CLAIM_TYPE, tokenType.name());

        if (roles != null && !roles.isEmpty()) {
            builder.claim(CLAIM_ROLES, roles);
        }
        else if (tokenType == JwtTokenType.ACCESS) {
            throw new ApplicationException(ErrorCode.JWT_INVALID_ROLES);
        }

        if (tokenType == JwtTokenType.REFRESH || tokenType == JwtTokenType.ACCESS) {
            builder.jwtID(UUID.randomUUID().toString());
        }

        return builder.build();
    }

    private void saveRefreshToken(JWTClaimsSet claimsSet) {
        String jti = claimsSet.getJWTID();
        String email = claimsSet.getSubject();
        if (email == null || email.isEmpty()) {
            throw new ApplicationException(ErrorCode.JWT_INVALID_SUBJECT);
        }
        if (claimsSet.getExpirationTime() == null) {
            throw new ApplicationException(ErrorCode.JWT_EXPIRED);
        }
        Instant expirationTime = claimsSet.getExpirationTime().toInstant();

        redisRefreshTokenStore.saveRefreshToken(jti, email, expirationTime);
    }

    private String getSecretForType(JwtTokenType tokenType) {
        return switch (tokenType) {
            case ACTIVATION -> activationProps.getSecret();
            case ACCESS -> accessProps.getSecret();
            case REFRESH -> refreshProps.getSecret();
            case PASSWORD_RESET -> passwordResetProps.getSecret();
            default -> throw new ApplicationException(
                    ErrorCode.JWT_INVALID_TYPE,
                    "Invalid JWT type: " + tokenType.name()
            );
        };
    }

    private long getValidityForType(JwtTokenType tokenType) {
        return switch (tokenType) {
            case ACTIVATION -> activationProps.getValidityInSeconds();
            case ACCESS -> accessProps.getValidityInSeconds();
            case REFRESH -> refreshProps.getValidityInSeconds();
            case PASSWORD_RESET -> passwordResetProps.getValidityInSeconds();
            default -> throw new ApplicationException(
                    ErrorCode.JWT_INVALID_TYPE,
                    "Invalid JWT type: " + tokenType.name()
            );
        };
    }
}
