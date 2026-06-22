package pl.m22.gamehive.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.common.exception.ApiValidationError;
import pl.m22.gamehive.user.dto.PageUserResponseDto;
import pl.m22.gamehive.user.dto.UpdateUserRolesDto;
import pl.m22.gamehive.user.dto.UserResponseDto;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Users", description = "Zarządzanie kontami użytkowników. Wymaga uwierzytelnienia JWT oraz roli ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień (wymagana rola ROLE_ADMIN)",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class AdminUserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @Operation(
            summary = "Lista użytkowników (stronicowana)",
            description = "Zwraca stronicowaną listę wszystkich użytkowników. Parametry stronicowania: page, size, sort.")
    @ApiResponse(responseCode = "200", description = "Strona wyników z użytkownikami",
            content = @Content(schema = @Schema(implementation = PageUserResponseDto.class)))
    @GetMapping("/")
    public ResponseEntity<Page<UserResponseDto>> getAllUsers(Pageable pageable) {

        Page<UserResponseDto> page = userService.findAllUsers(pageable)
                .map(userMapper::toUserResponseDto);

        return ResponseEntity.ok(page);
    }

    @Operation(summary = "Pobierz użytkownika po identyfikatorze")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Znaleziono użytkownika"),
            @ApiResponse(responseCode = "400", description = "Nieprawidłowy format UUID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(
            @Parameter(description = "Identyfikator użytkownika (UUID)", required = true) @PathVariable UUID id) {

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserById(id));

        return ResponseEntity.ok(userResponseDto);
    }

    @Operation(summary = "Pobierz użytkownika po nazwie użytkownika")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Znaleziono użytkownika"),
            @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/by-username/{username}")
    public ResponseEntity<UserResponseDto> getUserByUsername(
            @Parameter(description = "Nazwa użytkownika", required = true) @PathVariable String username) {

        Username usernameObj = new Username(username);

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserByUsername(usernameObj));

        return ResponseEntity.ok(userResponseDto);
    }

    @Operation(summary = "Pobierz użytkownika po adresie e-mail")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Znaleziono użytkownika"),
            @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/by-email/{email}")
    public ResponseEntity<UserResponseDto> getUserByEmail(
            @Parameter(description = "Adres e-mail użytkownika", required = true) @PathVariable String email) {

        Email emailObj = new Email(email);

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.findUserByEmail(emailObj));

        return ResponseEntity.ok(userResponseDto);
    }

    @Operation(
            summary = "Zmiana ról użytkownika",
            description = "Zastępuje role użytkownika podanym zestawem. Operacja audytowana (ROLE_CHANGE).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role zaktualizowane"),
            @ApiResponse(responseCode = "400", description = "Błąd walidacji lub niepoprawny UUID",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}/roles")
    public ResponseEntity<UserResponseDto> updateUserRoles(
            @Parameter(description = "Identyfikator użytkownika (UUID)", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRolesDto dto, Authentication authentication) {

        Email requester = new Email(authentication.getName());

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.updateUserRoles(id, dto.roles(), requester));

        return ResponseEntity.ok(userResponseDto);
    }

    @Operation(
            summary = "Dezaktywacja konta",
            description = "Wyłącza konto użytkownika i natychmiast unieważnia jego tokeny. Operacja audytowana (DEACTIVATE).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Konto dezaktywowane"),
            @ApiResponse(responseCode = "400", description = "Nieprawidłowy format UUID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<UserResponseDto> deactivateUser(
            @Parameter(description = "Identyfikator użytkownika (UUID)", required = true) @PathVariable UUID id,
            Authentication authentication) {

        Email requester = new Email(authentication.getName());

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.deactivateUser(id, requester));

        return ResponseEntity.ok(userResponseDto);
    }

    @Operation(
            summary = "Aktywacja (reaktywacja) konta",
            description = "Ponownie włącza wcześniej dezaktywowane konto. Operacja audytowana (ACTIVATE). Uwaga: poprzednie sesje pozostają unieważnione — wymagane ponowne logowanie.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Konto aktywowane"),
            @ApiResponse(responseCode = "400", description = "Nieprawidłowy format UUID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/activate")
    public ResponseEntity<UserResponseDto> activateUser(
            @Parameter(description = "Identyfikator użytkownika (UUID)", required = true) @PathVariable UUID id,
            Authentication authentication) {

        Email requester = new Email(authentication.getName());

        UserResponseDto userResponseDto = userMapper.toUserResponseDto(userService.activateUser(id, requester));

        return ResponseEntity.ok(userResponseDto);
    }

    @Operation(
            summary = "Usunięcie konta",
            description = "Trwale usuwa konto użytkownika i unieważnia jego tokeny. Operacja audytowana (DELETE).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Konto usunięte (brak treści)"),
            @ApiResponse(responseCode = "400", description = "Nieprawidłowy format UUID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponseDto> deleteUser(
            @Parameter(description = "Identyfikator użytkownika (UUID)", required = true) @PathVariable UUID id,
            Authentication authentication) {

        Email requester = new Email(authentication.getName());

        userService.deleteUser(id, requester);

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Wymuszenie wylogowania",
            description = "Unieważnia wszystkie aktywne sesje (tokeny) użytkownika bez zmiany stanu konta. Operacja audytowana (FORCE_LOGOUT).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesje unieważnione (brak treści)"),
            @ApiResponse(responseCode = "400", description = "Nieprawidłowy format UUID",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Użytkownik nie istnieje (USER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/force-logout")
    public ResponseEntity<Void> forceLogout(
            @Parameter(description = "Identyfikator użytkownika (UUID)", required = true) @PathVariable UUID id,
            Authentication authentication) {

        Email requester = new Email(authentication.getName());

        userService.forceLogoutUser(id, requester);

        return ResponseEntity.noContent().build();
    }
}
