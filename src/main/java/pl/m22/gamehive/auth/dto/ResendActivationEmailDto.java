package pl.m22.gamehive.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Żądanie ponownego wysłania wiadomości aktywacyjnej.")
public record ResendActivationEmailDto(
        @Schema(description = "Adres e-mail konta, na który ma zostać ponownie wysłany link aktywacyjny.",
                example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email must not be blank")
        String email) {
}