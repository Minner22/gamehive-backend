package pl.m22.gamehive.auth.jwt.service;

import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.TokenPairDto;
import pl.m22.gamehive.auth.jwt.JwtTokenType;

import java.util.Set;

public interface JwtService {
    String generateToken(String subjectEmail, JwtTokenType tokenType, Set<String> roles);
    void validateToken(String token, JwtTokenType tokenType);
    TokenPairDto generateTokenPair(CredentialsDto credentials);
    String extractEmailFromToken(String token);

    void revokeUsersTokens(String email);
}
