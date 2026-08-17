package pl.m22.gamehive.game.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.game.dto.AuthorDto;
import pl.m22.gamehive.game.dto.CategoryDto;
import pl.m22.gamehive.game.dto.MechanicDto;
import pl.m22.gamehive.game.dto.PublisherDto;
import pl.m22.gamehive.game.mapper.AuthorMapper;
import pl.m22.gamehive.game.mapper.CategoryMapper;
import pl.m22.gamehive.game.mapper.MechanicMapper;
import pl.m22.gamehive.game.mapper.PublisherMapper;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.search.service.TaxonomySuggestService;
import pl.m22.gamehive.game.service.TaxonomyService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/taxonomy")
@RequiredArgsConstructor
@Tag(name = "Taxonomy",
        description = "Słowniki fazy Gry (kategorie, mechaniki, wydawcy, autorzy) w trybie tylko do odczytu — "
                + "źródło identyfikatorów dla zgłoszenia gry. Wymaga uwierzytelnienia JWT (dowolna rola).")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class TaxonomyController {

    private static final String DEFAULT_SUGGEST_LIMIT = "10";
    private static final int MAX_SUGGEST_LIMIT = 50;

    private final AuthorMapper authorMapper;
    private final CategoryMapper categoryMapper;
    private final MechanicMapper mechanicMapper;
    private final PublisherMapper publisherMapper;
    private final TaxonomyService taxonomyService;
    private final TaxonomySuggestService taxonomySuggestService;

    @Operation(summary = "Lista kategorii")
    @ApiResponse(responseCode = "200", description = "Lista kategorii",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategoryDto.class))))
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> listCategories() {

        return ResponseEntity.ok(categoryMapper.toDtoList(taxonomyService.findAllCategories()));
    }

    @Operation(summary = "Lista mechanik")
    @ApiResponse(responseCode = "200", description = "Lista mechanik",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MechanicDto.class))))
    @GetMapping("/mechanics")
    public ResponseEntity<List<MechanicDto>> listMechanics() {

        return ResponseEntity.ok(mechanicMapper.toDtoList(taxonomyService.findAllMechanics()));
    }

    @Operation(summary = "Lista wydawców (opcjonalny filtr statusu)")
    @ApiResponse(responseCode = "200", description = "Lista wydawców",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PublisherDto.class))))
    @GetMapping("/publishers")
    public ResponseEntity<List<PublisherDto>> listPublishers(
            @Parameter(description = "Filtr po statusie (PENDING/APPROVED); brak parametru = wszyscy wydawcy)")
            @RequestParam(required = false) TaxonomyStatus status) {

        return ResponseEntity.ok(publisherMapper.toDtoList(taxonomyService.findPublishers(status)));
    }

    @Operation(summary = "Lista autorów (opcjonalny filtr statusu)")
    @ApiResponse(responseCode = "200", description = "Lista autorów",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AuthorDto.class))))
    @GetMapping("/authors")
    public ResponseEntity<List<AuthorDto>> listAuthors(
            @Parameter(description = "Filtr po statusie (PENDING/APPROVED); brak = wszyscy")
            @RequestParam(required = false) TaxonomyStatus status) {

        return ResponseEntity.ok(authorMapper.toDtoList(taxonomyService.findAuthors(status)));
    }

    @Operation(summary = "Podpowiedzi wydawców (autocomplete)",
            description = "Zwraca do `limit` najlepszych trafień po fragmencie nazwy — w kolejności trafności "
                    + "(Meilisearch) albo alfabetycznie (fallback bazodanowy), więc kolejność NIE jest częścią "
                    + "kontraktu. Wynik obejmuje wpisy w KAŻDYM statusie, także PENDING: formularz zgłoszenia "
                    + "reużywa istniejącą nazwę niezależnie od statusu, a ukrycie oczekującego wydawcy "
                    + "prowokowałoby duplikat, który wpadłby w konflikt unikalności. Pole `status` pozwala "
                    + "oznaczyć wpis jako oczekujący na akceptację. Brak lub pusta fraza zwraca początek listy. "
                    + "`limit` poza zakresem 1–" + MAX_SUGGEST_LIMIT + " jest zaciskany, nie odrzucany. "
                    + "Odpowiedź jest płaską listą — autocomplete nie stronicuje.")
    @ApiResponse(responseCode = "200", description = "Lista podpowiedzi",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PublisherDto.class))))
    @ApiResponse(responseCode = "503", description = "Wyszukiwarka nieosiągalna (SEARCH_INDEX_UNAVAILABLE)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/publishers/suggest")
    public ResponseEntity<List<PublisherDto>> suggestPublishers(
            @Parameter(description = "Fragment nazwy wydawcy; brak lub pusty = początek listy")
            @RequestParam(required = false) String q,
            @Parameter(description = "Maksymalna liczba podpowiedzi (1–" + MAX_SUGGEST_LIMIT + ", domyślnie 10)")
            @RequestParam(defaultValue = DEFAULT_SUGGEST_LIMIT) int limit) {

        return ResponseEntity.ok(taxonomySuggestService.suggestPublishers(q, clampLimit(limit)));
    }

    @Operation(summary = "Podpowiedzi autorów (autocomplete)",
            description = "Jak podpowiedzi wydawców, ale fraza dopasowuje imię, nazwisko ORAZ pełne "
                    + "„Imię Nazwisko\" — dopasowanie idzie po sklejonej nazwie, więc `uwe`, `rosenberg` "
                    + "i `uwe rosen` trafiają w ten sam wpis. Wynik obejmuje autorów w każdym statusie.")
    @ApiResponse(responseCode = "200", description = "Lista podpowiedzi",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AuthorDto.class))))
    @ApiResponse(responseCode = "503", description = "Wyszukiwarka nieosiągalna (SEARCH_INDEX_UNAVAILABLE)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/authors/suggest")
    public ResponseEntity<List<AuthorDto>> suggestAuthors(
            @Parameter(description = "Fragment imienia, nazwiska lub pełnej nazwy autora")
            @RequestParam(required = false) String q,
            @Parameter(description = "Maksymalna liczba podpowiedzi (1–" + MAX_SUGGEST_LIMIT + ", domyślnie 10)")
            @RequestParam(defaultValue = DEFAULT_SUGGEST_LIMIT) int limit) {

        return ResponseEntity.ok(taxonomySuggestService.suggestAuthors(q, clampLimit(limit)));
    }

    private static int clampLimit(int limit) {

        return Math.clamp(limit, 1, MAX_SUGGEST_LIMIT);
    }
}
