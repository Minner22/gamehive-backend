package pl.m22.gamehive.game.search.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.search.config.MeiliProperties;
import pl.m22.gamehive.game.search.dto.ContentReindexCounts;
import pl.m22.gamehive.game.search.dto.ReindexResultDto;
import pl.m22.gamehive.game.search.dto.TaxonomyReindexCounts;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchReindexServiceTest {

    private static final String LOCK_KEY = SearchReindexService.REINDEX_LOCK_KEY;

    @Mock GameSearchService gameSearchService;
    @Mock TaxonomySuggestService taxonomySuggestService;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private SearchReindexService service;

    /** token blokady powstaje w środku serwisu — podglądamy go na wejściu setIfAbsent */
    private final AtomicReference<String> acquiredToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        MeiliProperties properties = new MeiliProperties();
        properties.setReindexLockTtl(Duration.ofMinutes(15));

        service = new SearchReindexService(gameSearchService, taxonomySuggestService, redisTemplate, properties);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void stubLockFree() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), eq(Duration.ofMinutes(15))))
                .thenAnswer(invocation -> {
                    acquiredToken.set(invocation.getArgument(1));
                    return true;
                });
    }

    /** Zwolnienie idzie skryptem (compare-and-delete po stronie Redisa), więc weryfikujemy wysłany token. */
    private void verifyReleasedWithOwnToken() {
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), args.capture());
        assertThat(args.getValue()).isEqualTo(acquiredToken.get());
    }

    @Test
    @DisplayName("wolna blokada -> przebudowa OBU indeksów pod jedną blokadą, klucz zwalniany po zakończeniu")
    void reindex_rebuildsBothIndexesUnderOneLock() {
        stubLockFree();
        when(gameSearchService.reindexAll()).thenReturn(new ContentReindexCounts(3, 1));
        when(taxonomySuggestService.reindexAll()).thenReturn(new TaxonomyReindexCounts(5, 8));

        assertThat(service.reindex()).isEqualTo(new ReindexResultDto(3, 1, 5, 8));

        verify(gameSearchService).reindexAll();
        verify(taxonomySuggestService).reindexAll();
        verifyReleasedWithOwnToken();
    }

    @Test
    @DisplayName("zajęta blokada -> REINDEX_ALREADY_RUNNING (409) i ZERO ruchu w obu indeksach")
    void reindex_whenAlreadyRunning_throwsConflict() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.reindex())
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REINDEX_ALREADY_RUNNING);

        verifyNoInteractions(gameSearchService);
        verifyNoInteractions(taxonomySuggestService);
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("awaria przebudowy treści przerywa całość, NIE rusza słowników, ale zwalnia blokadę")
    void reindex_whenContentFails_skipsTaxonomyAndReleasesLock() {
        stubLockFree();
        when(gameSearchService.reindexAll())
                .thenThrow(new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE));

        assertThatThrownBy(() -> service.reindex()).isInstanceOf(InfrastructureException.class);

        verifyNoInteractions(taxonomySuggestService);
        verifyReleasedWithOwnToken();
    }

    @Test
    @DisplayName("awaria przebudowy słowników też nie zostawia zawieszonej blokady (finally)")
    void reindex_whenTaxonomyFails_stillReleasesLock() {
        stubLockFree();
        when(gameSearchService.reindexAll()).thenReturn(new ContentReindexCounts(1, 0));
        when(taxonomySuggestService.reindexAll())
                .thenThrow(new InfrastructureException(ErrorCode.SEARCH_FAILED));

        assertThatThrownBy(() -> service.reindex()).isInstanceOf(InfrastructureException.class);

        verifyReleasedWithOwnToken();
    }

    @Test
    @DisplayName("Redis niedostępny -> fail-open: reindeks (narzędzie naprawcze) i tak rusza, oba indeksy")
    void reindex_whenRedisDown_proceedsWithoutLock() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(gameSearchService.reindexAll()).thenReturn(new ContentReindexCounts(1, 0));
        when(taxonomySuggestService.reindexAll()).thenReturn(new TaxonomyReindexCounts(2, 3));

        assertThat(service.reindex()).isEqualTo(new ReindexResultDto(1, 0, 2, 3));

        verify(gameSearchService).reindexAll();
        verify(taxonomySuggestService).reindexAll();
    }
}
