package pl.m22.gamehive.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Profil użytkownika zwracany w odpowiedziach.")
public record UserProfileResponseDto(

        @Schema(description = "Imię.", example = "John")
        String firstName,
        @Schema(description = "Nazwisko.", example = "Doe")
        String lastName,
        @Schema(description = "Numer telefonu w formacie E.164.", example = "+48123456789")
        String phoneNumber,
        @Schema(description = "Adres użytkownika.")
        AddressDto address,
        @Schema(description = "Data urodzenia (ISO YYYY-MM-DD).", example = "1990-05-15")
        LocalDate dateOfBirth,
        @Schema(description = "URL zdjęcia profilowego.", example = "https://example.com/avatar.png")
        String profilePictureUrl
) {
}