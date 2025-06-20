package pl.m22.gamehive.auth.jwt.service;

public interface RefreshTokenService {
    public void revokeRefreshToken(String jti);
}
