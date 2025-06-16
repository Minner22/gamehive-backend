package pl.m22.gamehive.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginDto(
        @NotBlank(message = "Username or Email is mandatory") String usernameOrEmail,
        @NotBlank(message = "Password is mandatory") String password
) {
}
