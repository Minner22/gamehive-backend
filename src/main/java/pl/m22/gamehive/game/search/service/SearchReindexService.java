package pl.m22.gamehive.game.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.game.search.config.MeiliProperties;
import pl.m22.gamehive.game.search.dto.ContentReindexCounts;
import pl.m22.gamehive.game.search.dto.ReindexResultDto;
import pl.m22.gamehive.game.search.dto.TaxonomyReindexCounts;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchReindexService {

    public static final String REINDEX_LOCK_KEY = "search_reindex_lock";

    private static final RedisScript<Long> RELEASE_IF_OWNED = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final GameSearchService gameSearchService;
    private final TaxonomySuggestService taxonomySuggestService;
    private final RedisTemplate<String, String> redisTemplate;
    private final MeiliProperties properties;

    public ReindexResultDto reindex() {

        String token = UUID.randomUUID().toString();

        if (!acquire(token)) {
            throw new ApplicationException(ErrorCode.REINDEX_ALREADY_RUNNING);
        }

        try {
            ContentReindexCounts content = gameSearchService.reindexAll();
            TaxonomyReindexCounts taxonomy = taxonomySuggestService.reindexAll();

            return new ReindexResultDto(content.games(), content.expansions(),
                    taxonomy.publishers(), taxonomy.authors());
        } finally {
            release(token);
        }
    }

    private boolean acquire(String token) {

        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(REINDEX_LOCK_KEY, token, properties.getReindexLockTtl()));
        } catch (RedisConnectionFailureException _) {
            log.error("Redis unavailable while acquiring the reindex lock - proceeding without it");

            return true;
        }
    }

    private void release(String token) {

        try {
            redisTemplate.execute(RELEASE_IF_OWNED, List.of(REINDEX_LOCK_KEY), token);
        } catch (RedisConnectionFailureException _) {
            log.error("Redis unavailable while releasing the reindex lock - it will expire on its own");
        }
    }
}
