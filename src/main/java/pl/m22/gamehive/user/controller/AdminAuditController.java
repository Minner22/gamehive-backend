package pl.m22.gamehive.user.controller;

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
public class AdminAuditController {

    private final AuditLogQueryService auditLogQueryService;
    private final AuditLogMapper auditLogMapper;

    @GetMapping
    public ResponseEntity<Page<AuditLogResponseDto>> getAuditLog(
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false)AuditAction action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC)Pageable pageable) {

        AuditLogFilter filter = new AuditLogFilter(targetId, actor, action, from, to);

        Page<AuditLogResponseDto> page = auditLogQueryService.search(filter, pageable)
                .map(auditLogMapper::toAuditLogResponseDto);

        return ResponseEntity.ok(page);
    }
}
