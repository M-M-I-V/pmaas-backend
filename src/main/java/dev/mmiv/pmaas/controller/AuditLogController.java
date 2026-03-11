package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.entity.AuditLog;
import dev.mmiv.pmaas.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only audit log endpoint.
 * CORS is handled globally.
 *   The previous version injected AuditLogRepository directly. For read-only
 *   list queries with no additional logic this is acceptable; if pagination or
 *   access-level filtering is added in a future phase, extract to AuditLogService.
 * CORS is handled globally in WebSecurityConfiguration.
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping("/{entityName}/{recordId}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @PathVariable String entityName,
            @PathVariable int recordId) {
        List<AuditLog> logs = auditLogRepository
                .findByEntityNameAndRecordIdOrderByTimestampDesc(entityName, recordId);
        return ResponseEntity.ok(logs);
    }
}