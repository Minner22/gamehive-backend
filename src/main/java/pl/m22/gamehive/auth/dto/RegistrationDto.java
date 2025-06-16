package pl.m22.gamehive.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationDto(
        @NotBlank(message = "Username is mandatory") String username,
        @NotBlank(message = "Email is mandatory")
        @Email(message = "Email should be valid")
        String email,
        @NotBlank(message = "Password is mandatory")
        @Size(min = 5, message = "Password must be at least 5 characters long")
        String password) {
}
