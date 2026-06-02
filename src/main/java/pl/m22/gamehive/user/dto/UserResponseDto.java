package pl.m22.gamehive.user.dto;

import java.util.Set;
import java.util.UUID;

public record UserResponseDto(

        UUID id,
        String username,
        String email,
        boolean enabled,
        Set<String> roles,
        UserProfileResponseDto profile
) {
}
