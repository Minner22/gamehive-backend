package pl.m22.gamehive.game.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.game.dto.GameModerationDto;
import pl.m22.gamehive.game.dto.PageGameModerationDto;
import pl.m22.gamehive.game.dto.RejectGameRequestDto;
import pl.m22.gamehive.game.service.GameModerationService;

@RestController
@RequestMapping("/api/v1/moderation/games")
@PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Moderation - Games",
        description = "Kolejka zgłoszeń oczekujących i decyzje moderacyjne (approve/reject/unlock). "
                + "Wymaga uwierzytelnienia JWT oraz roli ROLE_MODERATOR lub ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień (wymagana rola MODERATOR/ADMIN)",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class GameModerationController {

    private final GameModerationService gameModerationService;

    @Operation(summary = "Kolejka zgłoszeń oczekujących (stronicowana)",
            description = "Gry w statusie PENDING oczekujące na decyzję. Parametry stronicowania: page, size, sort.")
    @ApiResponse(responseCode = "200", description = "Strona wyników z kolejką moderacji",
            content = @Content(schema = @Schema(implementation = PageGameModerationDto.class)))
    @GetMapping
    public ResponseEntity<Page<GameModerationDto>> pendingQueue(
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(gameModerationService.findPendingGames(pageable));
    }

    @Operation(summary = "Zatwierdź zgłoszenie",
            description = "PENDING → APPROVED, ustawia reviewedBy/reviewedAt. Zatwierdza również wszystkich "
                    + "wydawców i autorów gry o statusie PENDING (w tej samej transakcji).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zgłoszenie zatwierdzone"),
            @ApiResponse(responseCode = "404", description = "Gra nie istnieje (GAME_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Gra nie jest w kolejce PENDING (GAME_NOT_PENDING)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/approve")
    public ResponseEntity<GameModerationDto> approve(
            Authentication authentication,
            @PathVariable Long id) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(gameModerationService.approve(id, email));
    }

    @Operation(summary = "Odrzuć zgłoszenie",
            description = "PENDING → REJECTED z wymaganym powodem (widocznym dla autora), ustawia reviewedBy/reviewedAt.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zgłoszenie odrzucone"),
            @ApiResponse(responseCode = "400", description = "Brak powodu odrzucenia (REJECTION_REASON_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Gra nie istnieje (GAME_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Gra nie jest w kolejce PENDING (GAME_NOT_PENDING)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/reject")
    public ResponseEntity<GameModerationDto> reject(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody RejectGameRequestDto request) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(gameModerationService.reject(id, request.reason(), email));
    }

    @Operation(summary = "Odblokuj zgłoszenie po wyczerpaniu limitu poprawek",
            description = "REJECTED → DRAFT: zeruje resubmissionCount i czyści dane recenzji, pozwalając "
                    + "użytkownikowi ponownie edytować i wysłać zgłoszenie.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zgłoszenie odblokowane (DRAFT)"),
            @ApiResponse(responseCode = "404", description = "Gra nie istnieje (GAME_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Gra nie jest odrzucona (GAME_NOT_REJECTED)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/unlock")
    public ResponseEntity<GameModerationDto> unlock(
            Authentication authentication,
            @PathVariable Long id) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(gameModerationService.unlock(id, email));
    }
}
