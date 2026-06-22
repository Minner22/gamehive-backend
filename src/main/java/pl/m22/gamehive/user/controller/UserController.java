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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.common.exception.ApiValidationError;
import pl.m22.gamehive.user.dto.DeleteAccountDto;
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

    @Operation(
            summary = "Usunięcie własnego konta",
            description = "Trwale (hard delete) usuwa konto zalogowanego użytkownika po potwierdzeniu hasłem. "
                    + "Operacja jest nieodwracalna: unieważnia wszystkie tokeny odświeżające i bieżący token dostępowy "
                    + "oraz czyści ciasteczko refreshToken. Ostatni administrator nie może usunąć własnego konta.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Konto usunięte; tokeny unieważnione, ciasteczko wyczyszczone"),
            @ApiResponse(responseCode = "400", description = "Błąd walidacji danych wejściowych (brak hasła)",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "401",
                    description = "Brak lub nieprawidłowy token dostępowy albo błędne hasło potwierdzenia (INVALID_PASSWORD)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409",
                    description = "Ostatni administrator nie może usunąć własnego konta (CANNOT_REMOVE_LAST_ADMIN)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteOwnAccount(Authentication authentication,
                                                 @Valid @RequestBody DeleteAccountDto dto) {

        Email email = new Email(authentication.getName());

        userService.deleteOwnAccount(email, dto.password());

        ResponseCookie cleared = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth/refresh")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }
}