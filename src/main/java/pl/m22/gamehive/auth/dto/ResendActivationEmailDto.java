package pl.m22.gamehive.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendActivationEmailDto(
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email must not be blank")
        String email) {
}
