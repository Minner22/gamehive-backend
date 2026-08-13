package pl.m22.gamehive.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pl.m22.gamehive.common.logging.MdcTaskDecorator;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String AUTH_EMAIL_EXECUTOR = "authEmailExecutor";

    public static final String SEARCH_INDEX_EXECUTOR = "searchIndexExecutor";

    /**
     * Dedykowany, OGRANICZONY executor wysyłki maili auth (NIE SimpleAsyncTaskExecutor).
     * CallerRunsPolicy = backpressure: po przepełnieniu kolejki zadanie wykonuje wątek
     * zlecający, zamiast bez końca tworzyć wątki/odrzucać.
     * Sizing wyjściowy do dostrojenia po testach obciążeniowych.
     */
    @Bean(AUTH_EMAIL_EXECUTOR)
    @Profile("!test")
    public Executor authEmailExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("auth-email-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;
    }

    @Bean(SEARCH_INDEX_EXECUTOR)
    @Profile("!test")
    public Executor searchIndexExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("search-index-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler((_, _) ->
                log.error("Search index task rejected - queue full; index will drift until the next reindex"));
        executor.initialize();

        return executor;
    }
}
