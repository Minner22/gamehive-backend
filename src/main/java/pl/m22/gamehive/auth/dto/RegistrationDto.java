package pl.m22.gamehive.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationDto(
        @NotBlank(message = "Username is mandatory")
        @Size(min=3,max=30)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$")
        String username,
        @NotBlank(message = "Email is mandatory")
        @Email(message = "Email should be valid")
        String email,
        @NotBlank(message = "Password is mandatory")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        String password) {
}
