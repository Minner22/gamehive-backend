package pl.m22.gamehive.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import pl.m22.gamehive.common.logging.MdcTaskDecorator;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String AUTH_EMAIL_EXECUTOR = "authEmailExecutor";

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
        executor.initialize();

        return executor;
    }
}
