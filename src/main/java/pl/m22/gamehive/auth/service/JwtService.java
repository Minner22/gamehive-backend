package pl.m22.gamehive.auth.service;

import com.nimbusds.jwt.JWTClaimsSet;

public interface JwtService {

    String generateActivationToken(String subjectEmail);
    JWTClaimsSet validateActivationToken(String token);
}
