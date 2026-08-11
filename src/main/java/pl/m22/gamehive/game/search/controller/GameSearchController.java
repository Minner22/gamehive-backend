package pl.m22.gamehive.game.search.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.game.model.ContentModerationTargetType;
import pl.m22.gamehive.game.search.dto.GameSearchFilter;
import pl.m22.gamehive.game.search.dto.PageSearchResultDto;
import pl.m22.gamehive.game.search.dto.SearchResultDto;
import pl.m22.gamehive.game.search.service.GameSearchService;

@RestController
@RequestMapping("/api/v1/games/search")
@RequiredArgsConstructor
@Tag(name = "Games - Search",
        description = "Wyszukiwanie pełnotekstowe po tytule i opisie w zatwierdzonych grach ORAZ dodatkach, "
                + "z filtrami po wydawcy, kategorii, mechanice, autorze, liczbie graczy, czasie gry, roku i wieku. "
                + "Wymaga uwierzytelnienia JWT (dowolna rola).")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Nieprawidłowa wartość parametru (VALIDATION_ERROR) — "
                + "np. nieznany targetType albo niecałkowita wartość filtra liczbowego",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera lub odrzucone zapytanie wyszukiwarki (SEARCH_FAILED)",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "503", description = "Wyszukiwarka nieosiągalna (SEARCH_INDEX_UNAVAILABLE)",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class GameSearchController {

    private static final int MAX_PAGE_SIZE = 50;

    private final GameSearchService gameSearchService;

    @Operation(summary = "Szukaj w bibliotece (gry i dodatki, tylko APPROVED)",
            description = "Fraza q przeszukuje tytuł/nazwę, opis oraz tytuł gry bazowej dodatku. Kolejność wyników "
                    + "to ranking trafności, dlatego parametr sort jest IGNOROWANY — działają tylko page i size. "
                    + "Pusta lub brakująca fraza zwraca wszystko, co pasuje do filtrów. Dodatki są filtrowane po "
                    + "wartościach efektywnych (własnych lub odziedziczonych z gry bazowej); filtry po wydawcy, "
                    + "autorze i roku wydania dopasują wyłącznie gry, bo dodatek nie ma tych pól. Rozmiar strony "
                    + "jest ograniczony do 50, a łączna liczba trafień do limitu maxTotalHits wyszukiwarki (1000).")
    @ApiResponse(responseCode = "200", description = "Strona trafień w kolejności rankingu",
            content = @Content(schema = @Schema(implementation = PageSearchResultDto.class)))
    @GetMapping
    public ResponseEntity<Page<SearchResultDto>> search(
            @Parameter(description = "Fraza wyszukiwania (tytuł/nazwa, opis, tytuł gry bazowej)")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filtr: rodzaj treści — GAME albo EXPANSION (brak = jedno i drugie)")
            @RequestParam(required = false) ContentModerationTargetType targetType,
            @Parameter(description = "Filtr: id wydawcy (dopasuje tylko gry)")
            @RequestParam(required = false) Long publisherId,
            @Parameter(description = "Filtr: id kategorii")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Filtr: id mechaniki")
            @RequestParam(required = false) Long mechanicId,
            @Parameter(description = "Filtr: id autora (dopasuje tylko gry)")
            @RequestParam(required = false) Long authorId,
            @Parameter(description = "Filtr: id gry bazowej (dopasuje tylko dodatki)")
            @RequestParam(required = false) Long baseGameId,
            @Parameter(description = "Filtr: liczba graczy — pozycja obsługuje N graczy (minPlayers ≤ N ≤ maxPlayers)")
            @RequestParam(required = false) Integer players,
            @Parameter(description = "Filtr: maksymalny czas gry w minutach (playingTimeMinutes ≤ wartość)")
            @RequestParam(required = false) Integer maxPlayingTime,
            @Parameter(description = "Filtr: rok wydania (dokładny, dopasuje tylko gry)")
            @RequestParam(required = false) Integer yearPublished,
            @Parameter(description = "Filtr: wiek gracza — pozycja odpowiednia dla wieku N (minAge ≤ N)")
            @RequestParam(required = false) Integer age,
            @PageableDefault(size = 20) Pageable pageable) {

        GameSearchFilter filter = new GameSearchFilter(targetType, publisherId, categoryId, mechanicId, authorId,
                baseGameId, players, maxPlayingTime, yearPublished, age);

        return ResponseEntity.ok(gameSearchService.search(q, filter, clampPageSize(pageable)));
    }

    private static Pageable clampPageSize(Pageable pageable) {

        return pageable.getPageSize() <= MAX_PAGE_SIZE
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE);
    }
}
