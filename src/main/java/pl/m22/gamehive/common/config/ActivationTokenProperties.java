package pl.m22.gamehive.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt.activation")
public class ActivationTokenProperties {
    private String secret;
    private int validityInSeconds;
}
