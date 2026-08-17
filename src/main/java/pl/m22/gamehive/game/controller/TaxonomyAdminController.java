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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.common.exception.ApiValidationError;
import pl.m22.gamehive.game.dto.*;
import pl.m22.gamehive.game.mapper.AuthorMapper;
import pl.m22.gamehive.game.mapper.CategoryMapper;
import pl.m22.gamehive.game.mapper.MechanicMapper;
import pl.m22.gamehive.game.mapper.PublisherMapper;
import pl.m22.gamehive.game.model.TaxonomyStatus;
import pl.m22.gamehive.game.service.TaxonomyService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/taxonomy")
@PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Taxonomy",
        description = "Zarządzanie słownikami fazy Gry (kategorie, mechaniki, wydawcy, autorzy). "
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
public class TaxonomyAdminController {

    private final AuthorMapper authorMapper;
    private final TaxonomyService taxonomyService;
    private final CategoryMapper categoryMapper;
    private final MechanicMapper mechanicMapper;
    private final PublisherMapper publisherMapper;

    // category

    @Operation(summary = "Lista kategorii")
    @ApiResponse(responseCode = "200", description = "Lista kategorii",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategoryDto.class))))
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> listCategories() {

        return ResponseEntity.ok(categoryMapper.toDtoList(taxonomyService.findAllCategories()));
    }

    @Operation(summary = "Utwórz kategorię")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Kategoria utworzona"),
            @ApiResponse(responseCode = "400", description = "Błąd walidacji",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "409", description = "Nazwa zajęta (CATEGORY_NAME_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/categories")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody TaxonomyItemRequestDto request) {

        CategoryDto dto = categoryMapper.toDto(taxonomyService.createCategory(request.name()));

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Zmień nazwę kategorii")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nazwa zmieniona"),
            @ApiResponse(responseCode = "400", description = "Błąd walidacji / niepoprawne id",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "404", description = "Kategoria nie istnieje (CATEGORY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Nazwa zajęta (CATEGORY_NAME_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryDto> renameCategory(@PathVariable Long id,
                                                      @Valid @RequestBody TaxonomyItemRequestDto request) {

        return ResponseEntity.ok(categoryMapper.toDto(taxonomyService.renameCategory(id, request.name())));
    }

    @Operation(summary = "Usuń kategorię")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Kategoria usunięta (brak treści)"),
            @ApiResponse(responseCode = "404", description = "Kategoria nie istnieje (CATEGORY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Kategoria używana przez grę (CATEGORY_IN_USE)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {

        taxonomyService.deleteCategory(id);

        return ResponseEntity.noContent().build();
    }

    // mechanic

    @Operation(summary = "Lista mechanik")
    @ApiResponse(responseCode = "200", description = "Lista mechanik",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MechanicDto.class))))
    @GetMapping("/mechanics")
    public ResponseEntity<List<MechanicDto>> listMechanics() {

        return ResponseEntity.ok(mechanicMapper.toDtoList(taxonomyService.findAllMechanics()));
    }

    @Operation(summary = "Utwórz mechanikę")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mechanika utworzona"),
            @ApiResponse(responseCode = "400", description = "Błąd walidacji",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "409", description = "Nazwa zajęta (MECHANIC_NAME_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/mechanics")
    public ResponseEntity<MechanicDto> createMechanic(@Valid @RequestBody TaxonomyItemRequestDto request) {

        MechanicDto dto = mechanicMapper.toDto(taxonomyService.createMechanic(request.name()));

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Zmień nazwę mechaniki")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nazwa zmieniona"),
            @ApiResponse(responseCode = "400", description = "Błąd walidacji / niepoprawne id",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "404", description = "Mechanika nie istnieje (MECHANIC_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Nazwa zajęta (MECHANIC_NAME_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/mechanics/{id}")
    public ResponseEntity<MechanicDto> renameMechanic(@PathVariable Long id,
                                                      @Valid @RequestBody TaxonomyItemRequestDto request) {

        return ResponseEntity.ok(mechanicMapper.toDto(taxonomyService.renameMechanic(id, request.name())));
    }

    @Operation(summary = "Usuń mechanikę")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Mechanika usunięta (brak treści)"),
            @ApiResponse(responseCode = "404", description = "Mechanika nie istnieje (MECHANIC_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Mechanika używana przez grę (MECHANIC_IN_USE)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/mechanics/{id}")
    public ResponseEntity<Void> deleteMechanic(@PathVariable Long id) {

        taxonomyService.deleteMechanic(id);

        return ResponseEntity.noContent().build();

    }

    // publisher

    @Operation(summary = "Lista wydawców (stronicowana, filtr statusu i frazy)",
            description = "Odpowiedź jest stronicowana, bo lista wydawców rośnie wraz ze zgłoszeniami "
                    + "użytkowników. Fraza `q` dopasowuje fragment nazwy bez względu na wielkość liter; razem "
                    + "ze `status` działa koniunkcyjnie. Domyślnie 20 pozycji sortowanych po nazwie.")
    @ApiResponse(responseCode = "200", description = "Strona wydawców",
            content = @Content(schema = @Schema(implementation = PagePublisherDto.class)))
    @GetMapping("/publishers")
    public ResponseEntity<Page<PublisherDto>> listPublishers(
            @Parameter(description = "Filtr po statusie (PENDING/APPROVED); brak = wszyscy")
            @RequestParam(required = false) TaxonomyStatus status,
            @Parameter(description = "Filtr: fragment nazwy wydawcy; brak lub pusty = bez filtra")
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {

        return ResponseEntity.ok(taxonomyService.findPublishers(status, q, pageable).map(publisherMapper::toDto));
    }

    @Operation(summary = "Utwórz wydawcę", description = "Tworzy wydawcę od razu ze statusem APPROVED.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Wydawca utworzony"),
            @ApiResponse(responseCode = "400", description = "Błąd walidacji",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "409", description = "Nazwa zajęta (PUBLISHER_NAME_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/publishers")
    public ResponseEntity<PublisherDto> createPublisher(@Valid @RequestBody TaxonomyItemRequestDto request) {

        PublisherDto dto = publisherMapper.toDto(taxonomyService.createPublisher(request.name()));

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Zatwierdź wydawcę",
            description = "Zmienia status PENDING → APPROVED. Idempotentne: wydawca już APPROVED zwraca 200 bez zmian.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wydawca zatwierdzony (lub już był APPROVED)"),
            @ApiResponse(responseCode = "404", description = "Wydawca nie istnieje (PUBLISHER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/publishers/{id}/approve")
    public ResponseEntity<PublisherDto> approvePublisher(@PathVariable Long id) {

        return ResponseEntity.ok(publisherMapper.toDto(taxonomyService.approvePublisher(id)));
    }

    @Operation(summary = "Usuń wydawcę")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Wydawca usunięty (brak treści)"),
            @ApiResponse(responseCode = "404", description = "Wydawca nie istnieje (PUBLISHER_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Wydawca używany przez grę (PUBLISHER_IN_USE)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/publishers/{id}")
    public ResponseEntity<Void> deletePublisher(@PathVariable Long id) {

        taxonomyService.deletePublisher(id);

        return ResponseEntity.noContent().build();
    }

    // author

    @Operation(summary = "Lista autorów (stronicowana, filtr statusu i frazy)",
            description = "Jak lista wydawców, ale fraza `q` dopasowuje imię, nazwisko ORAZ pełne "
                    + "„Imię Nazwisko\" — po sklejonej nazwie, spójnie z endpointem podpowiedzi. "
                    + "Domyślnie 20 pozycji sortowanych po nazwisku, potem imieniu.")
    @ApiResponse(responseCode = "200", description = "Strona autorów",
            content = @Content(schema = @Schema(implementation = PageAuthorDto.class)))
    @GetMapping("/authors")
    public ResponseEntity<Page<AuthorDto>> listAuthors(
            @Parameter(description = "Filtr po statusie (PENDING/APPROVED); brak = wszyscy")
            @RequestParam(required = false) TaxonomyStatus status,
            @Parameter(description = "Filtr: fragment imienia, nazwiska lub pełnej nazwy; brak = bez filtra")
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = {"lastName", "firstName"}) Pageable pageable) {

        return ResponseEntity.ok(taxonomyService.findAuthors(status, q, pageable).map(authorMapper::toDto));
    }

    @Operation(summary = "Utwórz autora", description = "Tworzy autora od razu ze statusem APPROVED.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Autor utworzony"),
            @ApiResponse(responseCode = "400", description = "Błąd walidacji",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "409", description = "Autor już istnieje (AUTHOR_NAME_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/authors")
    public ResponseEntity<AuthorDto> createAuthor(@Valid @RequestBody AuthorRequestDto request) {

        AuthorDto dto = authorMapper.toDto(taxonomyService.createAuthor(request.firstName(), request.lastName()));

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Zatwierdź autora",
            description = "Zmienia status PENDING → APPROVED. Idempotentne: autor już APPROVED zwraca 200 bez zmian.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autor zatwierdzony (lub już był APPROVED)"),
            @ApiResponse(responseCode = "404", description = "Autor nie istnieje (AUTHOR_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/authors/{id}/approve")
    public ResponseEntity<AuthorDto> approveAuthor(@PathVariable Long id) {

        return ResponseEntity.ok(authorMapper.toDto(taxonomyService.approveAuthor(id)));
    }

    @Operation(summary = "Edytuj autora")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autor zaktualizowany"),
            @ApiResponse(responseCode = "400", description = "Błąd walidacji / niepoprawne id",
                    content = @Content(schema = @Schema(implementation = ApiValidationError.class))),
            @ApiResponse(responseCode = "404", description = "Autor nie istnieje (AUTHOR_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Para imię+nazwisko zajęta (AUTHOR_NAME_EXISTS)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/authors/{id}")
    public ResponseEntity<AuthorDto> updateAuthor(@PathVariable Long id,
                                                  @Valid @RequestBody AuthorRequestDto request) {

        return ResponseEntity.ok(authorMapper.toDto(taxonomyService.updateAuthor(id, request.firstName(), request.lastName())));
    }

    @Operation(summary = "Usuń autora")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Autor usunięty (brak treści)"),
            @ApiResponse(responseCode = "404", description = "Autor nie istnieje (AUTHOR_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Autor używany przez grę (AUTHOR_IN_USE)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/authors/{id}")
    public ResponseEntity<Void> deleteAuthor(@PathVariable Long id) {

        taxonomyService.deleteAuthor(id);

        return ResponseEntity.noContent().build();
    }
}
