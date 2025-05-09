package pl.m22.gamehive.user.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.m22.gamehive.user.dto.UserRegistrationDto;
import pl.m22.gamehive.user.exception.RoleNotFoundException;
import pl.m22.gamehive.user.service.UserService;

@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody UserRegistrationDto registrationDto) {
        if (userService.emailExists(registrationDto.email())) {
            return ResponseEntity.badRequest().body("Email already in use");
        }
        if (userService.usernameExists(registrationDto.username())) {
            return ResponseEntity.badRequest().body("Username already in use");
        }
        try {
            userService.register(registrationDto);
            return ResponseEntity.ok("User registered successfully. Please check your email to activate your account.");
        } catch (RoleNotFoundException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred during registration");
        }
    }
}
