package pl.m22.gamehive.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.user.dto.UserProfileResponseDto;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.dto.UserResponseDto;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> me(Authentication authentication) {

        String email = authentication.getName();
        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserByEmail(email));

        return ResponseEntity.ok(userResponseDto);
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<UserProfileResponseDto> profile(Authentication authentication,
                                                           @Valid @RequestBody UserProfileUpdateDto userProfileUpdateDto) {

        String email = authentication.getName();
        UserProfile updatedUserProfile = userService.updateCurrentUserProfile(email, userProfileUpdateDto);

        return ResponseEntity.ok(userMapper.toUserProfileResponseDto(updatedUserProfile));
    }
}
