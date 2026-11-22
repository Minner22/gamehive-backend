package pl.m22.gamehive.support;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.stream.Stream;

/**
 * GH-165: dwie postacie "Redis niedostępny", które obsługa awarii musi traktować tak samo.
 * Odmowa połączenia (Redis padł) → {@link RedisConnectionFailureException}; zawieszony Redis (przeciążenie, pauza
 * sieci, {@code docker pause}) → po {@code spring.data.redis.timeout} {@link QueryTimeoutException}. Każdy test
 * awarii Redis parametryzuj OBOMA — testy mockujące tylko pierwszy przepuściły błąd z #165.
 *
 * <p>Używane jako {@code @MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")}.
 */
public final class RedisUnavailability {

    private RedisUnavailability() {
    }

    public static Stream<RuntimeException> failures() {
        return Stream.of(
                new RedisConnectionFailureException("Unable to connect to Redis"),
                new QueryTimeoutException("Redis command timed out"));
    }
}
