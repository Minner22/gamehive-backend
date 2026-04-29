package pl.m22.gamehive.user.mapper;

import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
@RequiredArgsConstructor
public abstract class UserRoleMapper {

    protected UserRoleRepository userRoleRepository;

    @Named("mapToRoleNames")
    public Set<String> mapToRoleNames(Set<UserRole> roles) {
        return roles.stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapToUserRoles")
    public Set<UserRole> mapToUserRoles(Set<String> roleNames) {
        return roleNames.stream()
                .map(this::toUserRole)
                .collect(Collectors.toSet());
    }

    public UserRole toUserRole(String roleName) {
        return userRoleRepository.findByName(roleName)
                .orElseThrow(() -> new DomainException(ErrorCode.ROLE_NOT_FOUND));
    }
}