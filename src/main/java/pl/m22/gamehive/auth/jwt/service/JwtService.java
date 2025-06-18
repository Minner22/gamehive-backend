package pl.m22.gamehive.auth.jwt.service;

import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginResponseDto;
import pl.m22.gamehive.auth.jwt.JwtTokenType;

import java.util.Set;

public interface JwtService {

    String generateToken(String subjectEmail, JwtTokenType tokenType);
    String generateToken(String subjectEmail, JwtTokenType tokenType, Set<String> roles);
    String validateToken(String token, JwtTokenType tokenType);
    LoginResponseDto login(CredentialsDto credentials);
}
