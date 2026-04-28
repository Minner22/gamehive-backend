package pl.m22.gamehive.user.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleMapperTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @InjectMocks
    private UserRoleMapperImpl userRoleMapper;

    private UserRole adminRole;
    private UserRole userRole;

    @BeforeEach
    void setUp() {
        adminRole = new UserRole();
        adminRole.setId(1L);
        adminRole.setName("ROLE_ADMIN");

        userRole = new UserRole();
        userRole.setId(2L);
        userRole.setName("ROLE_USER");
    }

    @Test
    @DisplayName("mapToRoleNames() -> konwertuje Set<UserRole> na Set<String>")
    void mapToRoleNames_converts_roles_to_names() {
        Set<UserRole> roles = Set.of(adminRole, userRole);

        Set<String> result = userRoleMapper.mapToRoleNames(roles);

        assertEquals(2, result.size());
        assertTrue(result.contains("ROLE_ADMIN"));
        assertTrue(result.contains("ROLE_USER"));
    }

    @Test
    @DisplayName("mapToUserRoles() -> konwertuje Set<String> na Set<UserRole>")
    void mapToUserRoles_converts_names_to_roles() {
        when(userRoleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRoleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));

        Set<UserRole> result = userRoleMapper.mapToUserRoles(Set.of("ROLE_ADMIN", "ROLE_USER"));

        assertEquals(2, result.size());
        assertTrue(result.contains(adminRole));
        assertTrue(result.contains(userRole));
    }

    @Test
    @DisplayName("toUserRole() -> rola znaleziona -> zwraca UserRole")
    void toUserRole_found() {
        when(userRoleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));

        UserRole result = userRoleMapper.toUserRole("ROLE_ADMIN");

        assertEquals(adminRole, result);
        assertEquals("ROLE_ADMIN", result.getName());
    }

    @Test
    @DisplayName("toUserRole() -> rola nieznaleziona -> DomainException ROLE_NOT_FOUND")
    void toUserRole_not_found_throws_domain_exception() {
        when(userRoleRepository.findByName("ROLE_UNKNOWN")).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> userRoleMapper.toUserRole("ROLE_UNKNOWN"));

        assertEquals(ErrorCode.ROLE_NOT_FOUND, ex.getErrorCode());
    }
}