package pl.m22.gamehive.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dane rejestracji nowego konta użytkownika.")
public record RegistrationDto(
        @Schema(description = "Nazwa użytkownika: 3-30 znaków, dozwolone litery, cyfry oraz znaki . _ -",
                example = "john.doe", minLength = 3, maxLength = 30, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Username is mandatory")
        @Size(min=3,max=30)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$")
        String username,
        @Schema(description = "Adres e-mail w poprawnym formacie. Na ten adres trafi link aktywacyjny.",
                example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Email is mandatory")
        @Email(message = "Email should be valid")
        String email,
        @Schema(description = "Hasło: minimum 8 znaków.",
                example = "password123", minLength = 8, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Password is mandatory")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        String password) {
}