package pl.m22.gamehive.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Reprezentacja użytkownika zwracana przez API.")
public record UserResponseDto(

        @Schema(description = "Identyfikator użytkownika (UUID).", example = "018f4e2a-7c1d-7a3b-9b2e-0a1b2c3d4e5f")
        UUID id,
        @Schema(description = "Nazwa użytkownika.", example = "john.doe")
        String username,
        @Schema(description = "Adres e-mail.", example = "john.doe@example.com")
        String email,
        @Schema(description = "Czy konto jest aktywne.", example = "true")
        boolean enabled,
        @Schema(description = "Role przypisane użytkownikowi (z prefiksem ROLE_).", example = "[\"ROLE_USER\"]")
        Set<String> roles,
        @Schema(description = "Profil użytkownika.")
        UserProfileResponseDto profile
) {
}