package pl.m22.gamehive.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.common.exception.ApiValidationError;
import pl.m22.gamehive.user.dto.UserProfileResponseDto;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.dto.UserResponseDto;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "Operacje self-service zalogowanego użytkownika na własnym koncie i profilu.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @Operation(
            summary = "Dane zalogowanego użytkownika",
            description = "Zwraca dane konta i profil aktualnie uwierzytelnionego użytkownika (na podstawie tokenu dostępowego).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dane użytkownika"),
            @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> me(Authentication authentication) {

        Email email = new Email(authentication.getName());
        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserByEmail(email));

        return ResponseEntity.ok(userResponseDto);
    }

    @Operation(
            summary = "Aktualizacja profilu",
            description = "Częściowo aktualizuje profil zalogowanego użytkownika. Przesyłane są tylko pola do zmiany; pominięte pozostają bez zmian.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil zaktualizowany"),
            @ApiResponse(responseCode = "400", description = "Błąd walidacji danych wejściowych",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/me/profile")
    public ResponseEntity<UserProfileResponseDto> profile(Authentication authentication,
                                                          @Valid @RequestBody UserProfileUpdateDto userProfileUpdateDto) {

        Email email = new Email(authentication.getName());
        UserProfile updatedUserProfile = userService.updateCurrentUserProfile(email, userProfileUpdateDto);

        return ResponseEntity.ok(userMapper.toUserProfileResponseDto(updatedUserProfile));
    }
}