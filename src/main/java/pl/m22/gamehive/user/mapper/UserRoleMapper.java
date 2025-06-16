package pl.m22.gamehive.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Named;
import pl.m22.gamehive.user.model.UserRole;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserRoleMapper {

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
