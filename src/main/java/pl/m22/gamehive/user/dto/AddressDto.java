package pl.m22.gamehive.user.dto;

public record AddressDto(
        String street,
        String city,
        String postalCode,
        String country
) {
}
