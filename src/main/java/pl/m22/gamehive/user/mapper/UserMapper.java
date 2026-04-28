package pl.m22.gamehive.user.mapper;

import org.mapstruct.*;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.user.dto.UserProfileResponseDto;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.dto.UserResponseDto;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;

@Mapper(componentModel = "spring", uses = UserRoleMapper.class)
public abstract class UserMapper {

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapToUserRoles")
    public abstract AppUser toUser(CredentialsDto credentialsDto);

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapToRoleNames")
    public abstract CredentialsDto toCredentialsDto(AppUser appUser);

    public abstract AppUser toUser(RegistrationDto registrationDto);

    @Mapping(source = "roles", target = "roles", qualifiedByName = "mapToRoleNames")
    @Mapping(source = "userProfile", target = "profile")
    public abstract UserResponseDto toUserResponseDto(AppUser user);

    public abstract UserProfileResponseDto toUserProfileResponseDto(UserProfile profile);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateUserProfileFromDto(UserProfileUpdateDto dto, @MappingTarget UserProfile profile);
}