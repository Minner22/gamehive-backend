package pl.m22.gamehive.auth.jwt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.auth.jwt.config.AccessTokenProperties;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.common.logging.LoggingUtils;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisSessionEpochStore {

    private static final String PREFIX = "token_invalid_after:";

    private final RedisTemplate<String, String> redisTemplate;
    private final AccessTokenProperties accessProps;

    public void invalidateNow(String email) {

        try {
            // TTL = żywotność ACCESS tokenu: po niej wszystkie access tokeny sprzed unieważnienia i tak
            // wygasły, więc epoch przestaje być potrzebny i Redis usuwa go sam.
            redisTemplate.opsForValue().set(
                    PREFIX + email,
                    String.valueOf(Instant.now().toEpochMilli()),
                    Duration.ofSeconds(accessProps.getValidityInSeconds())
            );
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable while setting session epoch for user: {}", LoggingUtils.obfuscateEmail(email), e);
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot set session epoch - Redis unavailable");
        }
    }

    /**
     * Fail-open: przy awarii Redis zwraca null (jak TokenBlacklistService.isBlacklisted) — nie wylogowujemy
     * wszystkich przy chwilowym blipie Redis. Świadomy trade-off dostępność > ścisłość w czasie awarii.
     */
    public Long getInvalidAfter(String email) {
        try {
            String value = redisTemplate.opsForValue().get(PREFIX + email);

            return value == null ? null : Long.valueOf(value);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable while reading session epoch for user: {}", LoggingUtils.obfuscateEmail(email), e);

            return null;
        }
    }
}
