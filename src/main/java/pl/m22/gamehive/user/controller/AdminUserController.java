package pl.m22.gamehive.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.dto.UpdateUserRolesDto;
import pl.m22.gamehive.user.dto.UserResponseDto;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.service.UserService;

import java.util.UUID;

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
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID id) {

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserById(id));

        return ResponseEntity.ok(userResponseDto);
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<UserResponseDto> getUserByUsername(@PathVariable String username) {

        Username usernameObj = new Username(username);

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserByUsername(usernameObj));

        return ResponseEntity.ok(userResponseDto);
    }

    @GetMapping("/by-email/{email}")
    public ResponseEntity<UserResponseDto> getUserByEmail(@PathVariable String email) {

        Email emailObj = new Email(email);

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserByEmail(emailObj));

        return ResponseEntity.ok(userResponseDto);
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<UserResponseDto> updateUserRoles(@PathVariable UUID id, @Valid @RequestBody UpdateUserRolesDto dto, Authentication authentication) {

        Email requester = new Email(authentication.getName());

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.updateUserRoles(id, dto.roles(), requester));

        return ResponseEntity.ok(userResponseDto);
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<UserResponseDto> deactivateUser(@PathVariable UUID id, Authentication authentication) {

        Email requester = new Email(authentication.getName());

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.deactivateUser(id, requester));

        return ResponseEntity.ok(userResponseDto);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<UserResponseDto> activateUser(@PathVariable UUID id, Authentication authentication) {

        Email requester = new Email(authentication.getName());

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.activateUser(id, requester));

        return ResponseEntity.ok(userResponseDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponseDto> deleteUser(@PathVariable UUID id, Authentication authentication) {

        Email requester = new Email(authentication.getName());

        userService.deleteUser(id, requester);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/force-logout")
    public ResponseEntity<Void> forceLogout(@PathVariable UUID id, Authentication authentication) {

        Email requester = new Email(authentication.getName());

        userService.forceLogoutUser(id, requester);

        return ResponseEntity.noContent().build();
    }
}
