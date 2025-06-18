package pl.m22.gamehive.auth.jwt.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.config.ActivationTokenProperties;
import pl.m22.gamehive.common.exception.*;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;
    private final ActivationTokenProperties activationProps;

    @Override
    public String generateActivationToken(String subjectEmail) {
        JWTClaimsSet claimSet = createPayload(subjectEmail, JwtTokenType.ACTIVATION);
        SignedJWT activationJWT = generateToken(claimSet, JwtTokenType.ACTIVATION);
        return activationJWT.serialize();

    }

    private JWTClaimsSet createPayload(String subjectEmail, JwtTokenType tokenType) {
        Instant expirationDate = Instant.now().plusSeconds(getValidityForType(tokenType));
        return new JWTClaimsSet.Builder()
                .subject(subjectEmail)
                .expirationTime(Date.from(expirationDate))
                .claim("type", tokenType)
                .issueTime(Date.from(Instant.now()))
                .build();
    }

    @Override
    public String validateActivationToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(new MACVerifier(activationProps.getSecret()))) {
                throw new InvalidJwtSignature();
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                throw new ExpiredActivationTokenException();
            }

            String type = claims.getStringClaim("type");
            if (!JwtTokenType.ACTIVATION.name().equals(type)) {
                throw new InvalidJwtTypeException(type);
            }

            return claims.getSubject();
        } catch (ParseException e) {
            throw new RuntimeParseException("Failed to parse JWT: " + e.getMessage());
        } catch (JOSEException e) {
            throw new RuntimeJOSEException("Failed to validate JWT: " + e.getMessage());
        }
    }

    private SignedJWT generateToken(JWTClaimsSet claimSet, JwtTokenType tokenType) {
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

    private String getSecretForType(JwtTokenType tokenType) {
        return switch (tokenType) {
            case ACTIVATION -> activationProps.getSecret();
            default -> throw new InvalidJwtTypeException(tokenType.name());
        };
    }

    private long getValidityForType(JwtTokenType tokenType) {
        return switch (tokenType) {
            case ACTIVATION -> activationProps.getValidityInSeconds();
            default -> throw new InvalidJwtTypeException(tokenType.name());
        };
    }
}
