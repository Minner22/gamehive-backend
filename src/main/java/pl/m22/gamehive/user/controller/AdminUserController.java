package pl.m22.gamehive.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.user.dto.UpdateUserRolesDto;
import pl.m22.gamehive.user.dto.UserResponseDto;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.service.UserService;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping("/")
    public ResponseEntity<Page<UserResponseDto>> getAllUsers(Pageable pageable) {

        Page<UserResponseDto> page = userService.findAllUsers(pageable)
                .map(userMapper::toUserResponseDto);

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id) {

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserById(id));

        return ResponseEntity.ok(userResponseDto);
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<UserResponseDto> getUserByUsername(@PathVariable String username) {

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserByUsername(username));

        return ResponseEntity.ok(userResponseDto);
    }

    @GetMapping("/by-email/{email}")
    public ResponseEntity<UserResponseDto> getUserByEmail(@PathVariable String email) {

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserByEmail(email));

        return ResponseEntity.ok(userResponseDto);
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<UserResponseDto> updateUserRoles(@PathVariable Long id, @Valid @RequestBody UpdateUserRolesDto dto, Authentication authentication) {

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.updateUserRoles(id, dto.roles(), authentication.getName()));

        return ResponseEntity.ok(userResponseDto);
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<UserResponseDto> deactivateUser(@PathVariable Long id, Authentication authentication) {

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.deactivateUser(id, authentication.getName()));

        return ResponseEntity.ok(userResponseDto);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<UserResponseDto> activateUser(@PathVariable Long id) {

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.activateUser(id));

        return ResponseEntity.ok(userResponseDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponseDto> deleteUser(@PathVariable Long id, Authentication authentication) {

        userService.deleteUser(id, authentication.getName());

        return ResponseEntity.noContent().build();
    }
}
