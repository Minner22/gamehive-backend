package pl.m22.gamehive.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Dane logowania użytkownika.")
public record LoginDto(
        @Schema(description = "Adres e-mail konta.", example = "john.doe@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Email is mandatory")
        @Email(message = "Email should be valid")
        String email,
        @Schema(description = "Hasło konta.", example = "password123",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Password is mandatory") String password
) {
}