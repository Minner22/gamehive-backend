package pl.m22.gamehive.game.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "gamehive.search.enabled", matchIfMissing = true)
public class MeiliClientConfig {

    @Bean
    Client meiliClient(MeiliProperties properties) {

        return new Client(new Config(properties.getHost(), properties.getApiKey()));
    }
}
