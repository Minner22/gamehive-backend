package pl.m22.gamehive.game.search.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.game.search.dto.ContentReindexCounts;
import pl.m22.gamehive.game.search.dto.TaxonomyReindexCounts;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Semantyka blokady przeciw PRAWDZIWEMU Redisowi (embedded). Test z mockiem nie odróżni
 * compare-and-delete od zwykłego DELETE, a właśnie o tę różnicę tu chodzi.
 */
@SpringBootTest
@ActiveProfiles("test")
class SearchReindexLockIntegrationTest {

    private static final String LOCK_KEY = SearchReindexService.REINDEX_LOCK_KEY;

    @Autowired SearchReindexService searchReindexService;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @MockitoBean GameSearchService gameSearchService;
    @MockitoBean TaxonomySuggestService taxonomySuggestService;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(LOCK_KEY);
        when(taxonomySuggestService.reindexAll()).thenReturn(new TaxonomyReindexCounts(0, 0));
    }

    @AfterEach
    void cleanup() {
        redisTemplate.delete(LOCK_KEY);
    }

    @Test
    @DisplayName("udany reindeks zwalnia własną blokadę")
    void reindex_releasesOwnLock() {
        when(gameSearchService.reindexAll()).thenReturn(new ContentReindexCounts(2, 1));

        searchReindexService.reindex();

        assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
    }

    @Test
    @DisplayName("wyjątek w przebudowie też zwalnia blokadę — kolejna próba nie dostaje 409")
    void reindex_whenReindexFails_releasesLock() {
        when(gameSearchService.reindexAll())
                .thenThrow(new InfrastructureException(ErrorCode.SEARCH_INDEX_UNAVAILABLE));

        assertThatThrownBy(() -> searchReindexService.reindex()).isInstanceOf(InfrastructureException.class);

        assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
    }

    /**
     * Przebieg dłuższy niż TTL: klucz wygasa w trakcie i zakłada go inna instancja. Zwolnienie
     * musi wtedy zostawić CUDZĄ blokadę — inaczej wpuścilibyśmy drugi równoległy deleteAllDocuments.
     */
    @Test
    @DisplayName("blokada przejęta w trakcie przebiegu NIE jest kasowana przy zwalnianiu")
    void reindex_doesNotDeleteLockTakenOverByAnotherRun() {
        when(gameSearchService.reindexAll()).thenAnswer(_ -> {
            redisTemplate.opsForValue().set(LOCK_KEY, "token-innej-instancji", Duration.ofMinutes(15));
            return new ContentReindexCounts(0, 0);
        });

        searchReindexService.reindex();

        assertThat(redisTemplate.opsForValue().get(LOCK_KEY)).isEqualTo("token-innej-instancji");
    }

    @Test
    @DisplayName("zajęta blokada -> 409 i cudzy klucz zostaje nietknięty")
    void reindex_whenLockHeld_throwsAndLeavesLockAlone() {
        redisTemplate.opsForValue().set(LOCK_KEY, "token-innej-instancji", Duration.ofMinutes(15));

        assertThatThrownBy(() -> searchReindexService.reindex())
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REINDEX_ALREADY_RUNNING);

        assertThat(redisTemplate.opsForValue().get(LOCK_KEY)).isEqualTo("token-innej-instancji");
    }
}
