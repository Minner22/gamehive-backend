package pl.m22.gamehive.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.dto.UserProfileResponseDto;
import pl.m22.gamehive.user.dto.UserResponseDto;
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
}