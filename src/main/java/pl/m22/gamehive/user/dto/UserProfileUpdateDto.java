package pl.m22.gamehive.user.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserProfileUpdateDto(


        @Size(max = 50, message = "First name must be at most 50 characters long") String firstName,
        @Size(max = 50, message = "Last name must be at most 50 characters long") String lastName,
        @Pattern(regexp = "^\\+?[0-9. ()-]{7,25}$", message = "Phone number is invalid") String phoneNumber,
        @Size(max = 255, message = "Address must be at most 255 characters long") String address,
        @Past LocalDate dateOfBirth, // ISO format: YYYY-MM-DD
        @Size(max = 512, message = "Profile picture URL must be at most 512 characters long") String profilePictureUrl
) {
}
