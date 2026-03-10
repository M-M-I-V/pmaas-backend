package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.entity.AuditLog;
import dev.mmiv.pmaas.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AuditLogController {
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/{entityName}/{recordId}")
    @PreAuthorize("hasAnyRole('MD','DMD','NURSE')")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @PathVariable String entityName,
            @PathVariable int recordId) {
        List<AuditLog> logs = auditLogRepository.findByEntityNameAndRecordIdOrderByTimestampDesc(entityName, recordId);
        return ResponseEntity.ok(logs);
    }
}
