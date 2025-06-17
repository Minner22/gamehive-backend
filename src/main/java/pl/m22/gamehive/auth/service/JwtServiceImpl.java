package pl.m22.gamehive.auth.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.common.exception.JwtPrivateKeyLengthException;
import pl.m22.gamehive.common.exception.RuntimeJOSEException;

import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.activation.secret}")
    private String activationSecret;

    @Value("${jwt.activation.validityInSeconds}")
    private String activationTokenValidityInSeconds;

    private final JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;

    @Override
    public String generateActivationToken(String subjectEmail) {
        JWTClaimsSet claimSet = createActivationPayload(subjectEmail);
        SignedJWT activationJWT = generateToken(claimSet, activationSecret);
        return activationJWT.serialize();

    }

    private JWTClaimsSet createActivationPayload(String subjectEmail) {
        Instant expirationDate = Instant.now().plusSeconds(Integer.valueOf(activationTokenValidityInSeconds));
        return new JWTClaimsSet.Builder()
                .subject(subjectEmail)
                .expirationTime(Date.from(expirationDate))
                .claim("type", "activation")
                .issueTime(Date.from(Instant.now()))
                .build();
    }

    @Override
    public JWTClaimsSet validateActivationToken(String token) {
        // Implementation for validating activation token
        return null; // Replace with actual validation logic
    }

    private SignedJWT generateToken(JWTClaimsSet claimSet, String secret) {
        JWSHeader header = new JWSHeader(jwsAlgorithm);
        SignedJWT signedJWT = new SignedJWT(header, claimSet);
        try {
            JWSSigner jwsSigner = new MACSigner(secret);
            signedJWT.sign(jwsSigner);
            return signedJWT;
        } catch (KeyLengthException e) {
            throw new JwtPrivateKeyLengthException("Failed to create signer for JWT: " + e.getMessage());
        } catch (JOSEException e) {
            throw new RuntimeJOSEException("Failed to create JWT: " + e.getMessage());
        }
    }
}
