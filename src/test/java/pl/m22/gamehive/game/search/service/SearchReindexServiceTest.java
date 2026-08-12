package pl.m22.gamehive.game.search.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.search.config.MeiliProperties;
import pl.m22.gamehive.game.search.dto.ReindexResultDto;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchReindexServiceTest {

    private static final String LOCK_KEY = SearchReindexService.REINDEX_LOCK_KEY;

    @Mock GameSearchService gameSearchService;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private SearchReindexService service;

    /** token blokady powstaje w środku serwisu — podglądamy go na wejściu setIfAbsent */
    private final AtomicReference<String> acquiredToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        MeiliProperties properties = new MeiliProperties();
        properties.setReindexLockTtl(Duration.ofMinutes(15));

        service = new SearchReindexService(gameSearchService, redisTemplate, properties);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void stubLockFree() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofMinutes(15))))
                .thenAnswer(invocation -> {
                    acquiredToken.set(invocation.getArgument(1));
                    return true;
                });
        when(valueOperations.get(LOCK_KEY)).thenAnswer(_ -> acquiredToken.get());
    }

    @Test
    @DisplayName("wolna blokada -> reindeks leci, a klucz jest zwalniany po zakończeniu")
    void reindex_acquiresLockAndReleasesIt() {
        stubLockFree();
        when(gameSearchService.reindexAll()).thenReturn(new ReindexResultDto(3, 1));

        assertThat(service.reindex()).isEqualTo(new ReindexResultDto(3, 1));

        verify(gameSearchService).reindexAll();
        verify(redisTemplate).delete(LOCK_KEY);
    }

    @Test
    @DisplayName("zajęta blokada -> REINDEX_ALREADY_RUNNING (409) i ZERO ruchu w indeksie")
    void reindex_whenAlreadyRunning_throwsConflict() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.reindex())
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REINDEX_ALREADY_RUNNING);

        verifyNoInteractions(gameSearchService);
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("wyjątek w trakcie przebudowy nie zostawia zawieszonej blokady (finally)")
    void reindex_whenReindexFails_stillReleasesLock() {
        stubLockFree();
        when(gameSearchService.reindexAll())
                .thenThrow(new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE));

        assertThatThrownBy(() -> service.reindex()).isInstanceOf(InfrastructureException.class);

        verify(redisTemplate).delete(LOCK_KEY);
    }

    @Test
    @DisplayName("cudza blokada (przebieg dłuższy niż TTL) NIE jest kasowana przy zwalnianiu")
    void reindex_doesNotReleaseSomebodyElsesLock() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class))).thenReturn(true);
        when(valueOperations.get(LOCK_KEY)).thenReturn("token-innej-instancji");
        when(gameSearchService.reindexAll()).thenReturn(new ReindexResultDto(0, 0));

        service.reindex();

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("Redis niedostępny -> fail-open: reindeks (narzędzie naprawcze) i tak rusza")
    void reindex_whenRedisDown_proceedsWithoutLock() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(valueOperations.get(LOCK_KEY)).thenThrow(new RedisConnectionFailureException("redis down"));
        when(gameSearchService.reindexAll()).thenReturn(new ReindexResultDto(1, 0));

        assertThat(service.reindex()).isEqualTo(new ReindexResultDto(1, 0));

        verify(gameSearchService).reindexAll();
    }
}
