package pl.m22.gamehive.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Dane do częściowej aktualizacji profilu zalogowanego użytkownika. Wszystkie pola są opcjonalne — przesyłane są tylko te, które mają zostać zmienione.")
public record UserProfileUpdateDto(

        @Schema(description = "Imię (maks. 50 znaków).", example = "John", maxLength = 50)
        @Size(max = 50, message = "First name must be at most 50 characters long") String firstName,
        @Schema(description = "Nazwisko (maks. 50 znaków).", example = "Doe", maxLength = 50)
        @Size(max = 50, message = "Last name must be at most 50 characters long") String lastName,
        @Schema(description = "Numer telefonu w formacie E.164.", example = "+48123456789")
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be in E.164 format") String phoneNumber,
        @Schema(description = "Adres użytkownika.")
        AddressDto address,
        @Schema(description = "Data urodzenia w formacie ISO (YYYY-MM-DD); musi być w przeszłości.", example = "1990-05-15")
        @Past LocalDate dateOfBirth, // ISO format: YYYY-MM-DD
        @Schema(description = "URL zdjęcia profilowego (maks. 512 znaków).", example = "https://example.com/avatar.png", maxLength = 512)
        @Size(max = 512, message = "Profile picture URL must be at most 512 characters long") String profilePictureUrl
) {
}