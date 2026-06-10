package pl.m22.gamehive.auth.jwt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.auth.jwt.config.AccessTokenProperties;
import pl.m22.gamehive.auth.jwt.config.ActivationTokenProperties;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.common.logging.LoggingUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisSessionEpochStore {

    private static final String SESSION_PREFIX = "token_invalid_after:";
    private static final String ACTIVATION_PREFIX = "activation_invalid_after:";

    private final RedisTemplate<String, String> redisTemplate;
    private final AccessTokenProperties accessProps;
    private final ActivationTokenProperties activationProps;

    public void invalidateNow(String email) {

        // TTL = żywotność ACCESS tokenu: po niej wszystkie access tokeny sprzed unieważnienia i tak
        // wygasły, więc epoch przestaje być potrzebny i Redis usuwa go sam.
        setEpoch(SESSION_PREFIX, email, Instant.now().toEpochMilli(), accessProps.getValidityInSeconds());

    }

    public Long getInvalidAfter(String email) {

        return getEpoch(SESSION_PREFIX, email);
    }

    // Ucinamy do pełnych sekund: świeży token ma iat w sekundach (ucięte przy serializacji JWT).
    // Gdybyśmy zapisali epokę w ms, nowo wygenerowany token (iat = początek tej samej sekundy)
    // miałby iat < epoka i zostałby błędnie zrewokowany. Porównanie po stronie /activate jest ostre (<).
    public void invalidateActivationNow(String email) {

        setEpoch(ACTIVATION_PREFIX,
                email,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toEpochMilli(),
                activationProps.getValidityInSeconds()
        );
    }

    public Long getActivationInvalidAfter(String email) {

        return getEpoch(ACTIVATION_PREFIX, email);
    }

    private void setEpoch(String prefix, String email, long epochMillis, long ttlSeconds) {

        try {
            redisTemplate.opsForValue().set(
                    prefix + email,
                    String.valueOf(epochMillis),
                    Duration.ofSeconds(ttlSeconds)
            );
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable while setting epoch [{}] for user: {}", prefix, LoggingUtils.obfuscateEmail(email));
            throw new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot set epoch - Redis unavailable");
        }
    }

    /**
     * Fail-open: przy awarii Redis zwraca null (jak TokenBlacklistService.isBlacklisted) — nie wylogowujemy /
     * nie rewokujemy wszystkich przy chwilowym blipie Redis. Świadomy trade-off dostępność > ścisłość w czasie awarii.
     */
    private Long getEpoch(String prefix, String email) {

        try {
            String value = redisTemplate.opsForValue().get(prefix + email);

            return value == null ? null : Long.valueOf(value);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable while setting epoch [{}] for user: {}", prefix, LoggingUtils.obfuscateEmail(email));

            return null;
        }
    }

}
