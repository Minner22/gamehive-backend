package pl.m22.gamehive.auth.jwt.service;

import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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
    public void blacklistAccessToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String jti = signedJWT.getJWTClaimsSet().getJWTID();
            if (jti == null) {
                log.warn("Access token has no JTI, cannot blacklist");
                return;
            }

            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationTime == null) {
                return;
            }

            Duration remainingTtl = Duration.between(Instant.now(), expirationTime.toInstant());
            if (remainingTtl.isPositive()) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", remainingTtl);
            }
        } catch (ParseException e) {
            log.warn("Failed to parse token for blacklisting: {}", e.getMessage());
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable while blacklisting access token", e);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for blacklist check, failing open", e);
            return false;
        }
    }
}
