package pl.m22.gamehive.game.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.game.search.config.MeiliProperties;
import pl.m22.gamehive.game.search.dto.ReindexResultDto;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchReindexService {

    public static final String REINDEX_LOCK_KEY = "search_reindex_lock";

    private final GameSearchService gameSearchService;
    private final RedisTemplate<String, String> redisTemplate;
    private final MeiliProperties properties;

    public ReindexResultDto reindex() {

        String token = UUID.randomUUID().toString();

        if (!acquire(token)) {
            throw new ApplicationException(ErrorCode.REINDEX_ALREADY_RUNNING);
        }

        try {
            return gameSearchService.reindexAll();
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
            if (token.equals(redisTemplate.opsForValue().get(REINDEX_LOCK_KEY))) {
                redisTemplate.delete(REINDEX_LOCK_KEY);
            }
        } catch (RedisConnectionFailureException _) {
            log.error("Redis unavailable while releasing the reindex lock - it will expire on its own");
        }
    }
}
