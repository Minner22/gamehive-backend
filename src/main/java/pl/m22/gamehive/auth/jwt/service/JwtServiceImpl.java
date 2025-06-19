package pl.m22.gamehive.auth.jwt.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginResponseDto;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.config.AccessTokenProperties;
import pl.m22.gamehive.auth.jwt.config.ActivationTokenProperties;
import pl.m22.gamehive.auth.jwt.config.RefreshTokenProperties;
import pl.m22.gamehive.common.exception.*;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;
    private final ActivationTokenProperties activationProps;
    private final AccessTokenProperties accessProps;
    private final RefreshTokenProperties refreshProps;
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLES = "roles";

    @Override
    public String validateToken(String token, JwtTokenType tokenType) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.getHeader().getAlgorithm().equals(jwsAlgorithm)) {
                throw new InvalidJwtAlgorithmException(signedJWT.getHeader().getAlgorithm().getName());
            }
            if (!signedJWT.verify(new MACVerifier(getSecretForType(tokenType)))) {
                throw new InvalidJwtSignature();
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                throw new ExpiredJwtTokenException();
            }

            String type = claims.getStringClaim(CLAIM_TYPE);
            if (!tokenType.name().equals(type)) {
                throw new InvalidJwtTypeException(type);
            }

            return claims.getSubject();
        } catch (ParseException e) {
            throw new RuntimeParseException("Failed to parse JWT: " + e.getMessage());
        } catch (JOSEException e) {
            throw new RuntimeJOSEException("Failed to validate JWT: " + e.getMessage());
        }
    }

    @Override
    public LoginResponseDto login(CredentialsDto credentials) {
        String accessToken = generateToken(credentials.email(), JwtTokenType.ACCESS, credentials.roles());
        String refreshToken = generateToken(credentials.email(), JwtTokenType.REFRESH);

        return new LoginResponseDto(accessToken, refreshToken);
    }

    @Override
    public String generateToken(String subjectEmail, JwtTokenType tokenType) {
        JWTClaimsSet claimsSet = createPayload(subjectEmail, tokenType);
        SignedJWT signedJwt = generateSignedJwt(claimsSet, tokenType);
        return signedJwt.serialize();
    }

    @Override
    public String generateToken(String subjectEmail, JwtTokenType tokenType, Set<String> roles) {
        JWTClaimsSet claimsSet = createPayload(subjectEmail, tokenType, roles);
        SignedJWT signedJwt = generateSignedJwt(claimsSet, tokenType);
        return signedJwt.serialize();
    }

    @Override
    public String extractEmailFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet jwtClaimsSet = signedJWT.getJWTClaimsSet();
            if (jwtClaimsSet.getSubject() == null || jwtClaimsSet.getSubject().isEmpty()) {
                throw new InvalidJwtSubjectException();
            }
            return jwtClaimsSet.getSubject();
        } catch (ParseException e) {
            throw new RuntimeParseException("Failed to parse JWT: " + e.getMessage());
        }
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
            throw new JwtPrivateKeyLengthException("Failed to create signer for JWT: " + e.getMessage());
        } catch (JOSEException e) {
            throw new RuntimeJOSEException("Failed to create JWT: " + e.getMessage());
        }
    }

    private JWTClaimsSet createPayload(String subjectEmail, JwtTokenType tokenType) {
        Instant now = Instant.now();
        Instant expirationDate = now.plusSeconds(getValidityForType(tokenType));
        return new JWTClaimsSet.Builder()
                .subject(subjectEmail)
                .expirationTime(Date.from(expirationDate))
                .claim(CLAIM_TYPE, tokenType)
                .issueTime(Date.from(now))
                .build();
    }

    private JWTClaimsSet createPayload(String subjectEmail, JwtTokenType tokenType, Set<String> roles) {
        Instant now = Instant.now();
        Instant expirationDate = now.plusSeconds(getValidityForType(tokenType));
        return new JWTClaimsSet.Builder()
                .subject(subjectEmail)
                .expirationTime(Date.from(expirationDate))
                .claim(CLAIM_TYPE, tokenType)
                .claim(CLAIM_ROLES, roles)
                .issueTime(Date.from(now))
                .build();
    }

    private String getSecretForType(JwtTokenType tokenType) {
        return switch (tokenType) {
            case ACTIVATION -> activationProps.getSecret();
            case ACCESS -> accessProps.getSecret();
            case REFRESH -> refreshProps.getSecret();
            default -> throw new InvalidJwtTypeException(tokenType.name());
        };
    }

    private long getValidityForType(JwtTokenType tokenType) {
        return switch (tokenType) {
            case ACTIVATION -> activationProps.getValidityInSeconds();
            case ACCESS -> accessProps.getValidityInSeconds();
            case REFRESH -> refreshProps.getValidityInSeconds();
            default -> throw new InvalidJwtTypeException(tokenType.name());
        };
    }
}
