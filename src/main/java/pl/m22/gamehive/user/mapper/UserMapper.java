package pl.m22.gamehive.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import pl.m22.gamehive.user.dto.UserCredentialsDto;
import pl.m22.gamehive.user.dto.UserRegistrationDto;
import pl.m22.gamehive.user.model.AppUser;

@Mapper(uses = UserRoleMapper.class)
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapToUserRoles")
    AppUser toUser(UserCredentialsDto userCredentialsDto);

    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapToRoleNames")
    UserCredentialsDto toUserCredentialsDto(AppUser appUser);

    AppUser toUser(UserRegistrationDto userRegistrationDto);

    UserRegistrationDto toUserRegistrationDto(AppUser appUser);

}
