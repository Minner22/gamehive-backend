package pl.m22.gamehive.user.controller;

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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.m22.gamehive.common.exception.ApiError;
import pl.m22.gamehive.user.dto.AuditLogFilter;
import pl.m22.gamehive.user.dto.AuditLogResponseDto;
import pl.m22.gamehive.user.mapper.AuditLogMapper;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.service.AuditLogQueryService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Audit", description = "Przeglądanie dziennika audytu operacji na kontach. Wymaga uwierzytelnienia JWT oraz roli ROLE_ADMIN.")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditController {

    private final AuditLogQueryService auditLogQueryService;
    private final AuditLogMapper auditLogMapper;

    @Operation(
            summary = "Przeszukiwanie dziennika audytu",
            description = "Zwraca stronicowaną listę wpisów audytu z opcjonalnym filtrowaniem po użytkowniku, administratorze, rodzaju operacji i zakresie czasu. Domyślne sortowanie: createdAt malejąco.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Strona wyników z wpisami audytu"),
            @ApiResponse(responseCode = "400", description = "Nieprawidłowy parametr zapytania (np. zły UUID lub format daty)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Brak lub nieprawidłowy token dostępowy",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień (wymagana rola ROLE_ADMIN)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Błąd wewnętrzny serwera",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ResponseEntity<Page<AuditLogResponseDto>> getAuditLog(
            @Parameter(description = "Filtr: identyfikator (UUID) użytkownika, którego dotyczą operacje")
            @RequestParam(required = false) UUID targetId,
            @Parameter(description = "Filtr: e-mail administratora wykonującego operacje")
            @RequestParam(required = false) String actor,
            @Parameter(description = "Filtr: rodzaj operacji")
            @RequestParam(required = false)AuditAction action,
            @Parameter(description = "Filtr: początek zakresu czasu (ISO-8601, UTC), włącznie", example = "2026-06-01T00:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Filtr: koniec zakresu czasu (ISO-8601, UTC), włącznie", example = "2026-06-30T23:59:59Z")
            @RequestParam(required = false) Instant to,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC)Pageable pageable) {

        AuditLogFilter filter = new AuditLogFilter(targetId, actor, action, from, to);

        Page<AuditLogResponseDto> page = auditLogQueryService.search(filter, pageable)
                .map(auditLogMapper::toAuditLogResponseDto);

        return ResponseEntity.ok(page);
    }
}
