package pl.m22.gamehive.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserRegistrationDto(
        @NotBlank(message = "Username is mandatory") String username,
        @NotBlank(message = "Email is mandatory") String email,
        @NotBlank(message = "Password is mandatory") String password) {
}
