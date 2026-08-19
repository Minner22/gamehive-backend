package pl.m22.gamehive.game.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.json.GsonJsonHandler;
import com.meilisearch.sdk.json.JsonHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.m22.gamehive.game.search.service.MeiliIndexGateway;

@Configuration
@ConditionalOnProperty(name = "gamehive.search.enabled", matchIfMissing = true)
public class MeiliClientConfig {

    public static final String CONTENT_GATEWAY = "contentIndexGateway";

    public static final String TAXONOMY_GATEWAY = "taxonomyIndexGateway";

    @Bean
    JsonHandler meiliJsonHandler() {

        return new GsonJsonHandler();
    }

    @Bean
    Client meiliClient(MeiliProperties properties, JsonHandler jsonHandler) {

        return new Client(new Config(properties.getHost(), properties.getApiKey(), jsonHandler));
    }

    @Bean(CONTENT_GATEWAY)
    MeiliIndexGateway contentIndexGateway(Client client, JsonHandler jsonHandler, MeiliProperties properties) {

        return new MeiliIndexGateway(client, jsonHandler, properties.getIndexUid(),
                properties.getTaskWaitTimeout());
    }

    @Bean(TAXONOMY_GATEWAY)
    MeiliIndexGateway taxonomyIndexGateway(Client client, JsonHandler jsonHandler, MeiliProperties properties) {

        return new MeiliIndexGateway(client, jsonHandler, properties.getTaxonomyIndexUid(),
                properties.getTaskWaitTimeout());
    }
}
