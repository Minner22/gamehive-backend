package pl.m22.gamehive.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmDto(
      @NotBlank
      String token,
      @Size(min = 8, message = "Password must be at least 8 characters long")
      @NotBlank
      String newPassword) {
}
