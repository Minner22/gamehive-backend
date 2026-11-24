package pl.m22.gamehive.auth.jwt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.auth.jwt.RefreshTokenConsumeResult;
import pl.m22.gamehive.auth.jwt.config.RefreshTokenProperties;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.common.logging.LoggingUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRefreshTokenStore {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String USER_TOKENS_PREFIX = "user_refresh_tokens:";
    private static final String ROTATED_PREFIX = "refresh_rotated:";

    // KEYS: refresh_token:{oldJti}, user_refresh_tokens:{email}, refresh_rotated:{oldJti}, refresh_token:{newJti}
    // ARGV: oldJti, markerTtlMs, email, newJti, newTtlMs, scoreMs, userTokensTtlSec
    private static final RedisScript<Long> ROTATE = RedisScript.of("""
                    if redis.call('DEL', KEYS[1]) == 1 then
                        redis.call('ZREM', KEYS[2], ARGV[1])
                        redis.call('SET', KEYS[3], ARGV[3], 'PX', ARGV[2])
                        redis.call('SET', KEYS[4], ARGV[3], 'PX', ARGV[5])
                        redis.call('ZADD', KEYS[2], ARGV[6], ARGV[4])
                        redis.call('EXPIRE', KEYS[2], ARGV[7])
                        return 1
                    end
                    if redis.call('EXISTS', KEYS[3]) == 1 then
                        return 2
                    end
                    return 0
                    """, Long.class);

    // KEYS: user_refresh_tokens:{email}; ARGV: prefiks kluczy tokenów
    private static final RedisScript<Long> REVOKE_ALL = RedisScript.of("""
                    local jtis = redis.call('ZRANGE', KEYS[1], 0, -1)
                    for _, jti in ipairs(jtis) do
                        redis.call('DEL', ARGV[1] .. jti)
                    end
                    redis.call('DEL', KEYS[1])
                    return #jtis
                    """, Long.class);

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
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable while saving refresh token for user: {}", email, e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot save refresh token - Redis unavailable");
        }
    }

    public void revokeAllByUserEmail(String email) {
        try {
            redisTemplate.execute(REVOKE_ALL, List.of(USER_TOKENS_PREFIX + email), REFRESH_TOKEN_PREFIX);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable while revoking tokens for user: {}", email, e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot revoke tokens - Redis unavailable");
        }
    }

    public RefreshTokenConsumeResult rotate(String oldJti, String newJti, String email,
                                            Instant oldExpiresAt, Instant newExpiresAt) {

        Instant now = Instant.now();
        Duration markerTtl = Duration.between(now, oldExpiresAt);
        Duration newTtl = Duration.between(now, newExpiresAt);
        if (!markerTtl.isPositive() || !newTtl.isPositive()) {
            return RefreshTokenConsumeResult.UNKNOWN;
        }

        try {
            Long code = redisTemplate.execute(ROTATE,
                    List.of(REFRESH_TOKEN_PREFIX + oldJti, USER_TOKENS_PREFIX + email,
                            ROTATED_PREFIX + oldJti, REFRESH_TOKEN_PREFIX + newJti),
                    oldJti,
                    String.valueOf(markerTtl.toMillis()),
                    email,
                    newJti,
                    String.valueOf(newTtl.toMillis()),
                    String.valueOf(now.toEpochMilli()),
                    String.valueOf(refreshProps.getValidityInSeconds()));
            if (code == null) {
                return RefreshTokenConsumeResult.UNKNOWN;
            }

            return switch (code.intValue()) {
                case 1 -> RefreshTokenConsumeResult.CONSUMED;
                case 2 -> RefreshTokenConsumeResult.REUSED;
                default -> RefreshTokenConsumeResult.UNKNOWN;
            };

        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable while consuming refresh token for user: {}", LoggingUtils.obfuscateEmail(email), e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot rotate refresh token - Redis unavailable");
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
