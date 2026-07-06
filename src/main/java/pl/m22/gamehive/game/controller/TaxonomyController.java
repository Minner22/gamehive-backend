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

    private final AuthorMapper authorMapper;
    private final CategoryMapper categoryMapper;
    private final MechanicMapper mechanicMapper;
    private final PublisherMapper publisherMapper;
    private final TaxonomyService taxonomyService;

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

}
