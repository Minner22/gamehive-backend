package pl.m22.gamehive.auth.jwt.service;

import com.nimbusds.jwt.JWTClaimsSet;

public interface JwtService {

    String generateActivationToken(String subjectEmail);
    String validateActivationToken(String token);
}
