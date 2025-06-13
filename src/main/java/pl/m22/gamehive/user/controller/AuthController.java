package pl.m22.gamehive.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.m22.gamehive.user.dto.UserLoginDto;
import pl.m22.gamehive.user.dto.UserRegistrationDto;
import pl.m22.gamehive.common.exception.RoleNotFoundException;
import pl.m22.gamehive.user.service.UserService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody UserRegistrationDto registrationDto) {

        //TODO: add @ControllerAdvice to handle exceptions globally
        userService.register(registrationDto);
        return ResponseEntity.ok("User registration successful. Please check your email to confirm your account.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody UserLoginDto loginDto) {
        //TODO: add @ControllerAdvice to handle exceptions globally

        userService.login(loginDto);
        //TODO: add JWT generation and return it in response
        return ResponseEntity.ok("Login successful.");
    }
}
