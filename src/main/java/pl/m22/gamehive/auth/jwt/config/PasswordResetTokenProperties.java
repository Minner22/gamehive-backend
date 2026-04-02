package pl.m22.gamehive.auth.jwt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt.passwordreset")
public class PasswordResetTokenProperties {
    private String secret;
    private int validityInSeconds;
}
