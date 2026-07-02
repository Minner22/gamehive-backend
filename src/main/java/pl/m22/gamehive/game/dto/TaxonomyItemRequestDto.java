package pl.m22.gamehive.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Żądanie utworzenia/zmiany nazwy elementu słownika (kategoria, mechanika, wydawca).")
public record TaxonomyItemRequestDto(

        @Schema(description = "Nazwa elementu słownika (unikalna).", example = "Strategy",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 255) String name
) {
}
