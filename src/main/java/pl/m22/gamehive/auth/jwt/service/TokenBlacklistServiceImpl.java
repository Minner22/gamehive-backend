package pl.m22.gamehive.auth.jwt.service;

import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    // Wersje zwykłe i *Strict różnią się WYŁĄCZNIE reakcją na niedostępny Redis (GH-148/GH-168):
    // zwykłe połykają awarię (zapis) / przepuszczają (odczyt), strict rzucają REDIS_UNAVAILABLE.
    // Sama operacja na Redisie żyje w writeEntry / hasEntry.

    @Override
    public void blacklistToken(String token) {
        try {
            writeEntry(token);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable while blacklisting access token", e);
        }
    }

    @Override
    public void blacklistTokenStrict(String token) {
        try {
            writeEntry(token);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable while blacklisting access token", e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot blacklist token - Redis unavailable");
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        try {
            return hasEntry(jti);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.warn("Redis unavailable for blacklist check, failing open", e);
            return false;
        }
    }

    @Override
    public boolean isBlacklistedStrict(String jti) {
        try {
            return hasEntry(jti);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable for strict blacklist check, refusing the operation", e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot verify token blacklist - Redis unavailable");
        }
    }

    private void writeEntry(String token) {

        SignedJWT signedJWT;
        String jti;
        Date expirationDate;
        try {
            signedJWT = SignedJWT.parse(token);
            jti = signedJWT.getJWTClaimsSet().getJWTID();
            expirationDate = signedJWT.getJWTClaimsSet().getExpirationTime();
        } catch (ParseException e) {
            log.warn("Failed to parse token for blacklisting: {}", e.getMessage());
            return;
        }

        if (jti == null) {
            log.warn("Token has no JTI, cannot blacklist");
            return;
        }
        if (expirationDate == null) {
            return;
        }

        Duration remainingTtl = Duration.between(Instant.now(), expirationDate.toInstant());
        if (remainingTtl.isPositive()) {
            redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", remainingTtl);
        }
    }

    private boolean hasEntry(String jti) {

        return jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}
