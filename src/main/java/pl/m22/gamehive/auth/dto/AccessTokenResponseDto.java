package pl.m22.gamehive.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Odpowiedź z tokenem dostępowym po zalogowaniu lub odświeżeniu sesji.")
public record AccessTokenResponseDto(
        @Schema(description = "Token dostępowy JWT (ważny 15 min). Przekazuj w nagłówku `Authorization: Bearer <token>`.",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIn0.5f1c...")
        String accessToken
) {
}