package pl.m22.gamehive.auth.jwt.service;

import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.TokenPairDto;
import pl.m22.gamehive.auth.jwt.JwtTokenType;

import java.time.Instant;
import java.util.Set;

public interface JwtService {
    String generateToken(String subjectEmail, JwtTokenType tokenType, Set<String> roles);

    /**
     * Dla REFRESH nie sprawdza Redisa, więc trzeba użyć {@link #consumeRefreshToken}
     */
    void validateToken(String token, JwtTokenType tokenType);
    TokenPairDto generateTokenPair(CredentialsDto credentials);
    String extractEmailFromToken(String token);
    String extractJtiFromToken(String token);

    void revokeUsersTokens(String email);

    Instant extractIssuedAtFromToken(String token);

    /**
     * Jedyna poprawna ścieżka weryfikacji refresh tokena (GH-146): weryfikuje podpis/exp/typ, po czym ATOMOWO
     * go konsumuje (rotacja). Zwraca email właściciela. Ponowne użycie zrotowanego tokena unieważnia wszystkie
     * sesje użytkownika i kończy się REFRESH_TOKEN_REUSED; nieznany/wypchnięty/wylogowany → JWT_INVALID_JTI.
     */
    String consumeRefreshToken(String refreshToken);
}
