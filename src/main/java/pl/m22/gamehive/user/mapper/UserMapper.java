package pl.m22.gamehive.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.user.model.AppUser;

@Mapper(componentModel = "spring", uses = UserRoleMapper.class)
public interface UserMapper {

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapToUserRoles")
    AppUser toUser(CredentialsDto credentialsDto);

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapToRoleNames")
    CredentialsDto toCredentialsDto(AppUser appUser);

    AppUser toUser(RegistrationDto registrationDto);

    RegistrationDto toRegistrationDto(AppUser appUser);

}
