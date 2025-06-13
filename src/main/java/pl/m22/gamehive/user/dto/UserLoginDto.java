package pl.m22.gamehive.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserLoginDto(
        @NotBlank(message = "Username or Email is mandatory") String usernameOrEmail,
        @NotBlank(message = "Password is mandatory") String password
) {
}
