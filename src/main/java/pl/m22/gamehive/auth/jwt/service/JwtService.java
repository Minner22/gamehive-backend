package pl.m22.gamehive.auth.jwt.service;

import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.TokenPairDto;
import pl.m22.gamehive.auth.jwt.JwtTokenType;

import java.time.Instant;
import java.util.Set;
import java.util.function.Function;

public interface JwtService {
    String generateToken(String subjectEmail, JwtTokenType tokenType, Set<String> roles);

    /**
     * Dla REFRESH nie sprawdza Redisa, więc refresh trzeba weryfikować przez {@link #rotateRefreshToken}
     */
    void validateToken(String token, JwtTokenType tokenType);
    TokenPairDto generateTokenPair(CredentialsDto credentials);
    String extractEmailFromToken(String token);
    String extractJtiFromToken(String token);

    void revokeUsersTokens(String email);

    Instant extractIssuedAtFromToken(String token);

    /**
     * Jedyna poprawna ścieżka weryfikacji refresh tokena (GH-146): weryfikuje podpis/exp/typ, ładuje dane
     * właściciela ({@code credentialsLoader} dostaje email ze ZWERYFIKOWANEGO tokena), buduje nową parę tokenów
     * i ATOMOWO zamienia stary refresh na nowy (konsumpcja + zapis następcy w jednym skrypcie Redis).
     * Ponowne użycie zrotowanego tokena unieważnia wszystkie sesje użytkownika i kończy się REFRESH_TOKEN_REUSED;
     * nieznany/wypchnięty/wylogowany → JWT_INVALID_JTI. Przy błędzie żadne tokeny nie zostają wydane.
     */
    TokenPairDto rotateRefreshToken(String refreshToken, Function<String, CredentialsDto> credentialsLoader);
}
