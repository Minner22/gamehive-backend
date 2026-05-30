package pl.m22.gamehive.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_AUTH_STATE = "userAuthState";

    @Bean
    public CacheManager cacheManager() {

        CaffeineCacheManager cacheManager = new CaffeineCacheManager(USER_AUTH_STATE);

        // 60s = tylko safety net; realne unieważnienie idzie przez jawną eviction w UserSecurityEventListener.
        // Caveat multi-instance: Caffeine jest lokalny — eviction działa per-instancja; przy skalowaniu
        // poziomym TTL ogranicza staleness do <=60s albo trzeba cache w Redis / pub-sub eviction.
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(10_000));

        return cacheManager;
    }
}
