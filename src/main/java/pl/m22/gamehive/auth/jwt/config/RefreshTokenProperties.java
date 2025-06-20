package pl.m22.gamehive.auth.jwt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt.refresh")
public class RefreshTokenProperties {
    private String secret;
    private int validityInSeconds;
    private int maxActiveTokensPerUser;
}
