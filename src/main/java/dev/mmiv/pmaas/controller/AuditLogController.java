package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.entity.AuditLog;
import dev.mmiv.pmaas.service.AuditLogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Read-only audit log endpoint.
 * Previously injected AuditLogRepository directly, bypassing the service layer.
 *   All reads now go through AuditLogService.getLogsForRecord(). Any future additions —
 *   pagination, access-level filtering by role, response projection to hide the hash
 *   field from non-admin roles — can be applied in one place rather than scattered
 *   across controllers.
 * Write operations (recording audit events) are never exposed via HTTP. The only
 * write path is AuditLogService.record(), called internally from service classes.
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/{entityName}/{recordId}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
        @PathVariable String entityName,
        @PathVariable int recordId
    ) {
        return ResponseEntity.ok(
            auditLogService.getLogsForRecord(entityName, recordId)
        );
    }
}
