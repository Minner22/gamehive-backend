package pl.m22.gamehive.auth.jwt.service;

public interface TokenBlacklistService {
    void blacklistToken(String token);

    boolean isBlacklisted(String jti);
}
