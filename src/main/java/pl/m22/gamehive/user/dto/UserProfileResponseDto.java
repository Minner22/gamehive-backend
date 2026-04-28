package pl.m22.gamehive.user.dto;

import java.time.LocalDate;

public record UserProfileResponseDto(

        String firstName,
        String lastName,
        String phoneNumber,
        String address,
        LocalDate dateOfBirth,
        String profilePictureUrl
) {
}
