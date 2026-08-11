package pl.m22.gamehive.game.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gamehive.search")
public class MeiliProperties {

    private boolean enabled = true;

    private String host;

    private String apiKey;

    private String indexUid;

    private int reindexBatchSize;
}
