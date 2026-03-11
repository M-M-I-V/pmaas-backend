package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.entity.AuditLog;
import dev.mmiv.pmaas.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Centralised service for writing append-only, tamper-evident audit log entries.
 * Every entry is linked to the previous entry via a SHA-256 hash chain.
 * Any deletion or modification of a record in the database breaks the chain,
 * making tampering detectable by AuditIntegrityService (to be added in Phase 3).
 * Usage:
 *   auditLogService.record("Patients", patient.getId(), "CREATE", "Created patient record");
 *   auditLogService.record("AUTH",     0,                "LOGIN",  "Login from 192.168.1.1");
 * This service intentionally exposes ONLY record(). No read, update, or delete methods.
 * Reads are handled by AuditLogController → AuditLogRepository (read-only queries).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // Public API

    /**
     * Records an audit event for a specific entity record.
     * @param entityName The class/table name affected ("Patients", "MedicalVisits", "AUTH", etc.)
     * @param recordId   The primary key of the affected record. Use 0 for non-record events.
     * @param action     The action: CREATE | READ | UPDATE | DELETE | LOGIN | LOGIN_FAILED | LOGOUT
     * @param details    Human-readable description. Keep concise; never include PHI here.
     */
    public void record(String entityName, int recordId, String action, String details) {
        String username = resolveCurrentUsername();
        LocalDateTime timestamp = LocalDateTime.now();

        // Retrieve the previous hash to extend the chain.
        // If this is the very first entry, "GENESIS" anchors the chain.
        String prevHash = auditLogRepository.findTopByOrderByIdDesc()
                .map(AuditLog::getHash)
                .orElse("GENESIS");

        String hash = computeChainHash(entityName, recordId, action, username, timestamp, details, prevHash);

        AuditLog entry = AuditLog.builder()
                .entityName(entityName)
                .recordId(recordId)
                .action(action)
                .username(username)
                .timestamp(timestamp)
                .details(details)
                .hash(hash)
                .build();

        try {
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // Audit failure must NEVER crash the main operation.
            // Log the failure loudly so it can be investigated.
            log.error("AUDIT FAILURE — could not persist audit entry: entity={} id={} action={} user={}",
                    entityName, recordId, action, username, ex);
        }
    }

    // Private Helpers

    /**
     * Extracts the authenticated username from the SecurityContext.
     * Returns "SYSTEM" for automated/non-interactive operations.
     * Returns "ANONYMOUS" if somehow called before authentication.
     */
    private String resolveCurrentUsername() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // SecurityContext may not be available in async contexts.
        }
        return "SYSTEM";
    }

    /**
     * Computes a SHA-256 hash over all fields of the new entry plus the previous entry's hash.
     * The pipe character '|' is used as a field delimiter.
     * Breaking the chain (by deleting a record, modifying a field, or reordering records)
     * causes subsequent hashes to no longer match when the chain is re-verified.
     */
    private String computeChainHash(String entityName, int recordId, String action,
                                    String username, LocalDateTime timestamp,
                                    String details, String prevHash) {
        String payload = String.join("|",
                entityName,
                String.valueOf(recordId),
                action,
                username,
                timestamp.toString(),
                details != null ? details : "",
                prevHash
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed to be available by the Java spec.
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}