package pl.m22.gamehive.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

@Schema(description = "Zestaw ról do przypisania użytkownikowi (zastępuje dotychczasowe role).")
public record UpdateUserRolesDto(
        @Schema(description = "Niepusty zbiór nazw ról z prefiksem ROLE_.",
                example = "[\"ROLE_USER\", \"ROLE_ADMIN\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty Set<String> roles) {
}