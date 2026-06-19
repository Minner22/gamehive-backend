package pl.m22.gamehive.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.PhoneNumber;
import pl.m22.gamehive.common.domain.ProfilePictureUrl;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.dto.AddressDto;
import pl.m22.gamehive.user.dto.UserProfileResponseDto;
import pl.m22.gamehive.user.dto.UserResponseDto;
import pl.m22.gamehive.user.model.Address;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;

@Mapper(componentModel = "spring", uses = UserRoleMapper.class)
public abstract class UserMapper {

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapToRoleNames")
    public abstract CredentialsDto toCredentialsDto(AppUser appUser);

    @Mapping(source = "roles", target = "roles", qualifiedByName = "mapToRoleNames")
    @Mapping(source = "userProfile", target = "profile")
    public abstract UserResponseDto toUserResponseDto(AppUser user);

    public abstract UserProfileResponseDto toUserProfileResponseDto(UserProfile profile);

    public AddressDto toAddressDto(Address address) {
        if (address == null || address.isEmpty()) {

            return null;
        }

        return new AddressDto(
                address.getStreet(),
                address.getCity(),
                address.getPostalCode(),
                address.getCountry()
        );
    }

    public Address toAddress(AddressDto addressDto) {

        return addressDto == null
                ? null
                : Address.ofNullable(addressDto.street(), addressDto.city(), addressDto.postalCode(), addressDto.country());
    }

    public Email toEmail(String value) {
        return value == null ? null : new Email(value);
    }

    public String fromEmail(Email email) {
        return email == null ? null : email.value();
    }

    public Username toUsername(String value) {
        return value == null ? null : new Username(value);
    }

    public String fromUsername(Username username) {
        return username == null ? null : username.value();
    }

    public PhoneNumber toPhoneNumber(String value) {
        return value == null ? null : new PhoneNumber(value);
    }

    public String fromPhoneNumber(PhoneNumber phoneNumber) {
        return phoneNumber == null ? null : phoneNumber.value();
    }

    public ProfilePictureUrl toProfilePictureUrl(String value) {
        return value == null ? null : new ProfilePictureUrl(value);
    }

    public String fromProfilePictureUrl(ProfilePictureUrl url) {
        return url == null ? null : url.value();
    }

}