package pl.m22.gamehive.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Żądanie edycji autora (imię i nazwisko; para musi być unikalna).")
public record AuthorRequestDto(

        @Schema(description = "Imię autora.", example = "Uwe", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String firstName,

        @Schema(description = "Nazwisko autora.", example = "Rosenberg", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String lastName
) {
}
