package pl.m22.gamehive.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * W profilu test executory @Async działają INLINE (SyncTaskExecutor), więc @Async
 * wykonuje się synchronicznie na wątku wołającym -> verify(mailSender) / verify(gameSearchService)
 * zaraz po wywołaniu serwisu pozostaje deterministyczne, bez Awaitility.
 * Osobne profile (a nie bean-override): @Profile("!test") w AsyncConfig vs @Profile("test")
 * tutaj -> dokładnie jeden bean o danej nazwie aktywny per profil, brak konfliktu override.
 */

@Configuration
@Profile("test")
public class TestAsyncConfig {

    @Bean(AsyncConfig.AUTH_EMAIL_EXECUTOR)
    public Executor authEmailExecutor() {

        return new SyncTaskExecutor();
    }

    @Bean(AsyncConfig.SEARCH_INDEX_EXECUTOR)
    public Executor searchIndexExecutor() {

        return new SyncTaskExecutor();
    }

}
