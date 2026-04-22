package pl.m22.gamehive.user.dto;

import java.util.Set;

public record UserResponseDto(

        Long id,
        String username,
        String email,
        boolean enabled,
        Set<String> roles,
        UserProfileResponseDto profile
) {
}
