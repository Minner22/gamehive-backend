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
import pl.m22.gamehive.game.dto.GameExpansionDto;
import pl.m22.gamehive.game.dto.GameExpansionRequestDto;
import pl.m22.gamehive.game.dto.PageGameExpansionDto;
import pl.m22.gamehive.game.service.GameExpansionSubmissionService;

@RestController
@RequestMapping("/api/v1/expansions")
@RequiredArgsConstructor
@Tag(name = "Expansions",
        description = "Zgłaszanie dodatków do gier z biblioteki: tworzenie (DRAFT/PENDING), przegląd i edycja "
                + "własnych zgłoszeń, wysyłka do moderacji z limitem poprawek. Puste pola dodatku oznaczają "
                + "dziedziczenie wartości z gry bazowej. Wymaga uwierzytelnienia JWT (dowolna rola).")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class GameExpansionController {

    private final GameExpansionSubmissionService gameExpansionSubmissionService;

    @Operation(summary = "Utwórz zgłoszenie dodatku",
            description = "Tworzy zgłoszenie przypisane do zalogowanego użytkownika. Gra bazowa musi być APPROVED. "
                    + "Pole submit: true = od razu do moderacji (PENDING), false/brak = szkic (DRAFT).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Zgłoszenie utworzone"),
            @ApiResponse(responseCode = "400",
                    description = "Błąd walidacji (Bean Validation lub INVALID_PLAYER_COUNT dla wartości efektywnych)",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "404",
                    description = "Gra bazowa nie istnieje (GAME_NOT_FOUND) lub wskazany id słownika nie istnieje "
                            + "(CATEGORY_NOT_FOUND / MECHANIC_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Gra bazowa nie jest zatwierdzona (BASE_GAME_NOT_APPROVED)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<GameExpansionDto> createExpansion(
            Authentication authentication,
            @Valid @RequestBody GameExpansionRequestDto request) {

        Email email = new Email(authentication.getName());
        GameExpansionDto dto = gameExpansionSubmissionService.createExpansion(request, email);

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Moje zgłoszenia dodatków (stronicowane)",
            description = "Zgłoszenia zalogowanego użytkownika w statusach DRAFT/PENDING/REJECTED "
                    + "(zaakceptowane dodatki należą do biblioteki). Parametry stronicowania: page, size, sort.")
    @ApiResponse(responseCode = "200", description = "Strona wyników ze zgłoszeniami",
            content = @Content(schema = @Schema(implementation = PageGameExpansionDto.class)))
    @GetMapping("/my")
    public ResponseEntity<Page<GameExpansionDto>> mySubmissions(
            Authentication authentication,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(gameExpansionSubmissionService.findMySubmissions(email, pageable));
    }

    @Operation(summary = "Edytuj własne zgłoszenie dodatku",
            description = "Dozwolone tylko dla własnego wpisu w statusie DRAFT lub REJECTED. Własne kategorie "
                    + "i mechaniki są zastępowane w całości. Pola baseGameId i submit są ignorowane — dodatku "
                    + "nie da się przenieść na inną grę, a status zmienia wyłącznie POST /{id}/submit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zgłoszenie zaktualizowane"),
            @ApiResponse(responseCode = "400",
                    description = "Błąd walidacji (Bean Validation lub INVALID_PLAYER_COUNT dla wartości efektywnych)",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "404",
                    description = "Zgłoszenie nie istnieje lub należy do innego użytkownika (EXPANSION_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409",
                    description = "Zgłoszenie nieedytowalne w bieżącym statusie (EXPANSION_NOT_EDITABLE) "
                            + "albo gra bazowa przestała być zatwierdzona (BASE_GAME_NOT_APPROVED)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<GameExpansionDto> updateExpansion(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody GameExpansionRequestDto request) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(gameExpansionSubmissionService.updateExpansion(id, request, email));
    }

    @Operation(summary = "Wyślij zgłoszenie dodatku do moderacji",
            description = "DRAFT → PENDING (pierwsze wysłanie) albo REJECTED → PENDING (ponowne wysłanie: "
                    + "inkrementuje resubmissionCount i czyści powód odrzucenia). Liczbę ponownych wysyłek "
                    + "ogranicza limit gamehive.moderation.max-resubmissions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zgłoszenie wysłane do moderacji (PENDING)"),
            @ApiResponse(responseCode = "404",
                    description = "Zgłoszenie nie istnieje lub należy do innego użytkownika (EXPANSION_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409",
                    description = "Zły status (EXPANSION_NOT_EDITABLE), wyczerpany limit poprawek "
                            + "(RESUBMISSION_LIMIT_EXCEEDED) albo gra bazowa nie jest zatwierdzona (BASE_GAME_NOT_APPROVED)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/submit")
    public ResponseEntity<GameExpansionDto> submitExpansion(
            Authentication authentication,
            @PathVariable Long id) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(gameExpansionSubmissionService.submitExpansion(id, email));
    }
}
