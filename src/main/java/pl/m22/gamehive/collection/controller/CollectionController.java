package pl.m22.gamehive.collection.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.collection.dto.ExpansionCollectionItemDto;
import pl.m22.gamehive.collection.dto.GameCollectionItemDto;
import pl.m22.gamehive.collection.dto.PageExpansionCollectionItemDto;
import pl.m22.gamehive.collection.dto.PageGameCollectionItemDto;
import pl.m22.gamehive.collection.service.CollectionService;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.ApiError;

@RestController
@RequestMapping("/api/v1/collection")
@RequiredArgsConstructor
@Tag(name = "Collection",
        description = "Prywatna kolekcja zalogowanego użytkownika: dodawanie, usuwanie i listowanie "
                + "posiadanych gier oraz dodatków. Do kolekcji trafiają wyłącznie cele APPROVED z biblioteki. "
                + "Tożsamość pochodzi z tokenu — nie da się odczytać ani zmienić cudzej kolekcji. "
                + "Wymaga uwierzytelnienia JWT (dowolna rola).")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class CollectionController {

    private final CollectionService collectionService;

    @Operation(summary = "Moje gry (stronicowane)",
            description = "Gry w kolekcji zalogowanego użytkownika wraz z pełnymi danymi gry. "
                    + "Parametry stronicowania: page, size, sort.")
    @ApiResponse(responseCode = "200", description = "Strona wyników z kolekcji gier",
            content = @Content(schema = @Schema(implementation = PageGameCollectionItemDto.class)))
    @GetMapping("/games")
    public ResponseEntity<Page<GameCollectionItemDto>> myGames(
            Authentication authentication,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(collectionService.findMyGames(email, pageable));
    }

    @Operation(summary = "Dodaj grę do kolekcji",
            description = "Dodaje grę APPROVED do kolekcji zalogowanego użytkownika ze statusem OWNED. "
                    + "Ta sama gra może być w kolekcjach wielu użytkowników — unikat obejmuje parę (użytkownik, gra).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Gra dodana do kolekcji"),
            @ApiResponse(responseCode = "404", description = "Gra nie istnieje (GAME_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409",
                    description = "Gra nie jest zatwierdzona (GAME_NOT_APPROVED) albo jest już w kolekcji "
                            + "(ALREADY_IN_COLLECTION)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/games/{gameId}")
    public ResponseEntity<GameCollectionItemDto> addGame(
            Authentication authentication,
            @PathVariable Long gameId) {

        Email email = new Email(authentication.getName());
        GameCollectionItemDto dto = collectionService.addGame(gameId, email);

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Usuń grę z kolekcji",
            description = "Usuwa wyłącznie własny wpis. Gra w bibliotece pozostaje nietknięta. "
                    + "Cudzy wpis jest nieodróżnialny od nieistniejącego (404).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Wpis usunięty z kolekcji"),
            @ApiResponse(responseCode = "404",
                    description = "Gry nie ma w Twojej kolekcji (COLLECTION_ITEM_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/games/{gameId}")
    public ResponseEntity<Void> removeGame(
            Authentication authentication,
            @PathVariable Long gameId) {

        Email email = new Email(authentication.getName());
        collectionService.removeGame(gameId, email);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Moje dodatki (stronicowane)",
            description = "Dodatki w kolekcji zalogowanego użytkownika wraz z wartościami własnymi i efektywnymi. "
                    + "Parametry stronicowania: page, size, sort.")
    @ApiResponse(responseCode = "200", description = "Strona wyników z kolekcji dodatków",
            content = @Content(schema = @Schema(implementation = PageExpansionCollectionItemDto.class)))
    @GetMapping("/expansions")
    public ResponseEntity<Page<ExpansionCollectionItemDto>> myExpansions(
            Authentication authentication,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        Email email = new Email(authentication.getName());

        return ResponseEntity.ok(collectionService.findMyExpansions(email, pageable));
    }

    @Operation(summary = "Dodaj dodatek do kolekcji",
            description = "Dodaje dodatek APPROVED do kolekcji ze statusem OWNED — niezależnie od tego, "
                    + "czy jego gra bazowa jest w kolekcji.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dodatek dodany do kolekcji"),
            @ApiResponse(responseCode = "404", description = "Dodatek nie istnieje (EXPANSION_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409",
                    description = "Dodatek nie jest zatwierdzony (EXPANSION_NOT_APPROVED) albo jest już "
                            + "w kolekcji (ALREADY_IN_COLLECTION)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/expansions/{expansionId}")
    public ResponseEntity<ExpansionCollectionItemDto> addExpansion(
            Authentication authentication,
            @PathVariable Long expansionId) {

        Email email = new Email(authentication.getName());
        ExpansionCollectionItemDto dto = collectionService.addExpansion(expansionId, email);

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Usuń dodatek z kolekcji",
            description = "Usuwa wyłącznie własny wpis. Dodatek w bibliotece pozostaje nietknięty.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Wpis usunięty z kolekcji"),
            @ApiResponse(responseCode = "404",
                    description = "Dodatku nie ma w Twojej kolekcji (COLLECTION_ITEM_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/expansions/{expansionId}")
    public ResponseEntity<Void> removeExpansion(
            Authentication authentication,
            @PathVariable Long expansionId) {

        Email email = new Email(authentication.getName());
        collectionService.removeExpansion(expansionId, email);

        return ResponseEntity.noContent().build();
    }
}
