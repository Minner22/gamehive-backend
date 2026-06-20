package pl.m22.gamehive.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Potwierdzenie resetu hasła z tokenem otrzymanym mailem oraz nowym hasłem.")
public record PasswordResetConfirmDto(
      @Schema(description = "Token resetu hasła (JWT) otrzymany w wiadomości e-mail.",
              example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
      @NotBlank
      String token,
      @Schema(description = "Nowe hasło: minimum 8 znaków.",
              example = "newPassword1", minLength = 8, requiredMode = Schema.RequiredMode.REQUIRED)
      @Size(min = 8, message = "Password must be at least 8 characters long")
      @NotBlank
      String newPassword) {
}