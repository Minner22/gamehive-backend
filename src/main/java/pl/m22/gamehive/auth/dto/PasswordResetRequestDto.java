package pl.m22.gamehive.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Żądanie wysłania linku do resetu hasła.")
public record PasswordResetRequestDto(
        @Schema(description = "Adres e-mail konta, dla którego ma zostać zresetowane hasło.",
                example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email must not be blank")
        String email) {
}