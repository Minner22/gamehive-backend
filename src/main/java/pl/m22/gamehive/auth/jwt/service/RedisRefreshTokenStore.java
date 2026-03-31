package pl.m22.gamehive.auth.jwt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.auth.jwt.config.RefreshTokenProperties;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRefreshTokenStore {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String USER_TOKENS_PREFIX = "user_refresh_tokens:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RefreshTokenProperties refreshProps;

    public void saveRefreshToken(String jti, String email, Instant expiresAt) {
        try {
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            if (ttl.isNegative() || ttl.isZero()) {
                return;
            }

            redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + jti, email, ttl);

            String userTokensKey = USER_TOKENS_PREFIX + email;
            redisTemplate.opsForZSet().add(userTokensKey, jti, Instant.now().toEpochMilli());
            redisTemplate.expire(userTokensKey, Duration.ofSeconds(refreshProps.getValidityInSeconds()));

            evictOldestIfExceeded(email);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable while saving refresh token for user: {}", email, e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot save refresh token - Redis unavailable");
        }
    }

    public boolean existsByJti(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_TOKEN_PREFIX + jti));
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable while checking refresh token JTI: {}", jti, e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot verify refresh token - Redis unavailable");
        }
    }

    public void revokeAllByUserEmail(String email) {
        try {
            String userTokensKey = USER_TOKENS_PREFIX + email;
            Set<String> jtis = redisTemplate.opsForZSet().range(userTokensKey, 0, -1);

            if (jtis != null && !jtis.isEmpty()) {
                for (String jti : jtis) {
                    redisTemplate.delete(REFRESH_TOKEN_PREFIX + jti);
                }
            }

            redisTemplate.delete(userTokensKey);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable while revoking tokens for user: {}", email, e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot revoke tokens - Redis unavailable");
        }
    }

    private void evictOldestIfExceeded(String email) {
        String userTokensKey = USER_TOKENS_PREFIX + email;
        Long count = redisTemplate.opsForZSet().zCard(userTokensKey);

        if (count != null && count > refreshProps.getMaxActiveTokensPerUser()) {
            Set<String> oldest = redisTemplate.opsForZSet().range(userTokensKey, 0, 0);
            if (oldest != null && !oldest.isEmpty()) {
                String oldestJti = oldest.iterator().next();
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + oldestJti);
                redisTemplate.opsForZSet().remove(userTokensKey, oldestJti);
            }
        }
    }
}
