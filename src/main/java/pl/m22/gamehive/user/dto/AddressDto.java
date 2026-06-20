package pl.m22.gamehive.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Adres użytkownika.")
public record AddressDto(
        @Schema(description = "Ulica wraz z numerem.", example = "Marszałkowska 1")
        String street,
        @Schema(description = "Miasto.", example = "Warszawa")
        String city,
        @Schema(description = "Kod pocztowy.", example = "00-001")
        String postalCode,
        @Schema(description = "Kraj.", example = "Poland")
        String country
) {
}