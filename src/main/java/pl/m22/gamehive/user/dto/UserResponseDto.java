package pl.m22.gamehive.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Reprezentacja użytkownika zwracana przez API.")
public record UserResponseDto(

        @Schema(description = "Identyfikator użytkownika (UUID).", example = "018f4e2a-7c1d-7a3b-9b2e-0a1b2c3d4e5f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Nazwa użytkownika.", example = "john.doe", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @Schema(description = "Adres e-mail.", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "Czy konto jest aktywne.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean enabled,
        @Schema(description = "Role przypisane użytkownikowi (z prefiksem ROLE_).", example = "[\"ROLE_USER\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Set<String> roles,
        @Schema(description = "Profil użytkownika.", requiredMode = Schema.RequiredMode.REQUIRED)
        UserProfileResponseDto profile
) {
}