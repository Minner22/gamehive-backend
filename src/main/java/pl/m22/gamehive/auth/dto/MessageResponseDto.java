package pl.m22.gamehive.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Prosta odpowiedź informacyjna z komunikatem dla klienta.")
public record MessageResponseDto(
        @Schema(description = "Komunikat informacyjny dla klienta.",
                example = "User registration successful. Please check your email to confirm your account.")
        String message
) {
}