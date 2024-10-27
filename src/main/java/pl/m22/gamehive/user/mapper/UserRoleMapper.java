package pl.m22.gamehive.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import pl.m22.gamehive.user.model.UserRole;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public interface UserRoleMapper {

    UserRoleMapper INSTANCE = Mappers.getMapper(UserRoleMapper.class);

    @Named("mapToRoleName")
    default String toRoleName(UserRole role) {
        return role.getName();
    }

    UserRole toUserRole(String roleName);

    @Named("mapToRoleNames")
    default Set<String> mapToRoleNames(Set<UserRole> roles) {
        return roles.stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());
    }
    @Named("mapToUserRoles")
    default Set<UserRole> mapToUserRoles(Set<String> roleNames) {
        return roleNames.stream()
                .map(this::toUserRole)
                .collect(Collectors.toSet());
    }
}
