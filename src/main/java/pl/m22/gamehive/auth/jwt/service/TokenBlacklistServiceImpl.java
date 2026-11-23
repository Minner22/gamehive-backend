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

    @Override
    public void blacklistToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String jti = signedJWT.getJWTClaimsSet().getJWTID();
            if (jti == null) {
                log.warn("Token has no JTI, cannot blacklist");
                return;
            }

            Date expirationDate = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationDate == null) {
                return;
            }

            Instant expiration = expirationDate.toInstant();

            Duration remainingTtl = Duration.between(Instant.now(), expiration);
            if (remainingTtl.isPositive()) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", remainingTtl);
            }
        } catch (ParseException e) {
            log.warn("Failed to parse token for blacklisting: {}", e.getMessage());
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable while blacklisting access token", e);
        }
    }

    @Override
    public void blacklistTokenStrict(String token) {

        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String jti = signedJWT.getJWTClaimsSet().getJWTID();
            if (jti == null) {
                log.warn("Token has no JTI, cannot blacklist");
                return;
            }

            Date expirationDate = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationDate == null) {
                return;
            }

            Instant expiration = expirationDate.toInstant();

            Duration remainingTtl = Duration.between(Instant.now(), expiration);
            if (remainingTtl.isPositive()) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", remainingTtl);
            }
        } catch (ParseException e) {
            log.warn("Failed to parse token for blacklisting: {}", e.getMessage());
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable while blacklisting access token", e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot blacklist token - Redis unavailable");
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return redisTemplate.hasKey(BLACKLIST_PREFIX + jti);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.warn("Redis unavailable for blacklist check, failing open", e);
            return false;
        }
    }

    @Override
    public boolean isBlacklistedStrict(String jti) {

        if (jti == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable for strict blacklist check, refusing the operation", e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot verify token blacklist - Redis unavailable");
        }
    }
}
