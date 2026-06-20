package pl.m22.gamehive.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Globalna definicja dokumentu OpenAPI dla GameHive API.
 *
 * <p>Deklaruje metadane API (tytuł, wersja, opis, kontakt) oraz schemat bezpieczeństwa
 * {@code bearerAuth} (HTTP Bearer / JWT). Do schematu odwołują się poszczególne kontrolery
 * przez {@code @SecurityRequirement(name = "bearerAuth")} na endpointach zabezpieczonych JWT.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "GameHive API",
                version = "v1",
                description = """
                        REST API platformy GameHive.

                        Uwierzytelnianie odbywa się za pomocą tokenów JWT (Bearer). Token dostępowy
                        (access token) należy przekazywać w nagłówku `Authorization: Bearer <token>`.
                        Endpointy `/api/v1/auth/**` są publiczne; pozostałe wymagają ważnego tokenu,
                        a część operacji administracyjnych dodatkowo roli `ROLE_ADMIN`.
                        """,
                contact = @Contact(name = "GameHive", url = "https://github.com/Minner22/gamehive-backend"),
                license = @License(name = "MIT")
        )
)
@SecurityScheme(
        name = "bearerAuth",
        description = "Token dostępowy JWT. Przekazuj jako `Authorization: Bearer <accessToken>`.",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}