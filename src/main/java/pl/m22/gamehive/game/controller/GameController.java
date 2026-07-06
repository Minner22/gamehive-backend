package pl.m22.gamehive.game.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.common.exception.ApiValidationError;
import pl.m22.gamehive.game.dto.GameDto;
import pl.m22.gamehive.game.dto.GameRequestDto;
import pl.m22.gamehive.game.dto.PageGameDto;
import pl.m22.gamehive.game.service.GameSubmissionService;

@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
@Tag(name = "Games",
        description = "Zgłaszanie gier do globalnej biblioteki: tworzenie (DRAFT/PENDING), przegląd i edycja "
                + "własnych zgłoszeń, wysyłka do moderacji z limitem poprawek. Wymaga uwierzytelnienia JWT (dowolna rola).")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class GameController {

    private final GameSubmissionService gameSubmissionService;

    @Operation(summary = "Utwórz zgłoszenie gry",
            description = "Tworzy zgłoszenie przypisane do zalogowanego użytkownika. "
                    + "Pole submit: true = od razu do moderacji (PENDING), false/brak = szkic (DRAFT).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Zgłoszenie utworzone"),
            @ApiResponse(responseCode = "400",
                    description = "Błąd walidacji (Bean Validation lub INVALID_PLAYER_COUNT / PUBLISHER_REQUIRED / CATEGORY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "404",
                    description = "Wskazany id nie istnieje (PUBLISHER_NOT_FOUND / CATEGORY_NOT_FOUND / MECHANIC_NOT_FOUND / AUTHOR_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<GameDto> createGame(
            Authentication authentication,
            @Valid @RequestBody GameRequestDto request) {

        Email email = new Email(authentication.getName());
        GameDto dto = gameSubmissionService.createGame(request, email);

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Moje zgłoszenia (stronicowane)",
            description = "Zgłoszenia zalogowanego użytkownika w statusach DRAFT/PENDING/REJECTED "
                    + "(zaakceptowane gry należą do globalnej biblioteki). Parametry stronicowania: page, size, sort.")
    @ApiResponse(responseCode = "200", description = "Strona wyników ze zgłoszeniami",
            content = @Content(schema = @Schema(implementation = PageGameDto.class)))
    @GetMapping("/my")
    public ResponseEntity<Page<GameDto>> mySubmissions(
            Authentication authentication,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(gameSubmissionService.findMySubmissions(email, pageable));
    }

    @Operation(summary = "Pobierz własne zgłoszenie",
            description = "Zwraca grę zalogowanego użytkownika w dowolnym statusie. "
                    + "Cudze i nieistniejące zgłoszenia są nierozróżnialne (404).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Znaleziono zgłoszenie"),
            @ApiResponse(responseCode = "404",
                    description = "Zgłoszenie nie istnieje lub należy do innego użytkownika (GAME_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<GameDto> getMySubmission(
            Authentication authentication,
            @PathVariable Long id) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(gameSubmissionService.findMySubmission(id, email));
    }

    @Operation(summary = "Edytuj własne zgłoszenie",
            description = "Dozwolone tylko dla własnego wpisu w statusie DRAFT lub REJECTED. Relacje "
                    + "(wydawcy/kategorie/mechaniki/autorzy) są zastępowane w całości. Pole submit jest "
                    + "ignorowane — status zmienia wyłącznie POST /{id}/submit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zgłoszenie zaktualizowane"),
            @ApiResponse(responseCode = "400",
                    description = "Błąd walidacji (Bean Validation lub INVALID_PLAYER_COUNT / PUBLISHER_REQUIRED / CATEGORY_REQUIRED)",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "404",
                    description = "Zgłoszenie nie istnieje lub należy do innego użytkownika (GAME_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409",
                    description = "Zgłoszenie nieedytowalne w bieżącym statusie (GAME_NOT_EDITABLE)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<GameDto> updateGame(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody GameRequestDto request) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(gameSubmissionService.updateGame(id, request, email));
    }

    @Operation(summary = "Wyślij zgłoszenie do moderacji",
            description = "DRAFT → PENDING (pierwsze wysłanie) albo REJECTED → PENDING (ponowne wysłanie: "
                    + "inkrementuje resubmissionCount i czyści powód odrzucenia). Liczbę ponownych wysyłek "
                    + "ogranicza limit gamehive.moderation.max-resubmissions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zgłoszenie wysłane do moderacji (PENDING)"),
            @ApiResponse(responseCode = "404",
                    description = "Zgłoszenie nie istnieje lub należy do innego użytkownika (GAME_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409",
                    description = "Zły status (GAME_NOT_EDITABLE) albo wyczerpany limit poprawek (RESUBMISSION_LIMIT_EXCEEDED — status pozostaje REJECTED)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/submit")
    public ResponseEntity<GameDto> submitGame(
            Authentication authentication,
            @PathVariable Long id) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(gameSubmissionService.submitGame(id, email));
    }
}
