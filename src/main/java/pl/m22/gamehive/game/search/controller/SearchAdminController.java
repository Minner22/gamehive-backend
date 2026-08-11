package pl.m22.gamehive.game.search.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.game.search.dto.ReindexResultDto;
import pl.m22.gamehive.game.search.service.GameSearchService;

@RestController
@RequestMapping("/api/v1/admin/search")
@PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Search",
        description = "Utrzymanie indeksu wyszukiwania. Wymaga uwierzytelnienia JWT oraz roli "
                + "ROLE_MODERATOR lub ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień (wymagana rola MODERATOR/ADMIN)",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera lub odrzucone żądanie wyszukiwarki (SEARCH_FAILED)",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "503", description = "Wyszukiwarka nieosiągalna (SEARCH_INDEX_UNAVAILABLE)",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class SearchAdminController {

    private final GameSearchService gameSearchService;

    @Operation(summary = "Przebuduj indeks wyszukiwania z bazy",
            description = "Kasuje zawartość indeksu i wypycha ponownie wszystkie zatwierdzone gry i dodatki. "
                    + "Narzędzie naprawcze po awarii wyszukiwarki — indeksowanie bieżące dzieje się zdarzeniami "
                    + "po decyzjach moderacyjnych. Na czas przebudowy indeks bywa chwilowo niekompletny.")
    @ApiResponse(responseCode = "200", description = "Indeks przebudowany, w ciele liczniki dokumentów",
            content = @Content(schema = @Schema(implementation = ReindexResultDto.class)))
    @PostMapping("/reindex")
    public ResponseEntity<ReindexResultDto> reindex() {

        return ResponseEntity.ok(gameSearchService.reindexAll());
    }
}
