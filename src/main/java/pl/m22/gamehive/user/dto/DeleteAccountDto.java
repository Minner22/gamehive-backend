package pl.m22.gamehive.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Potwierdzenie usunięcia konta hasłem.")
public record DeleteAccountDto(

        @Schema(description = "Hasło użytkownika, wymagane do potwierdzenia usunięcia konta.",
                example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Password must not be blank")
        String password
) {
}
