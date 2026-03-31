package pl.m22.gamehive.auth.jwt.service;

public interface TokenBlacklistService {
    void blacklistAccessToken(String token);
    boolean isBlacklisted(String jti);
}
