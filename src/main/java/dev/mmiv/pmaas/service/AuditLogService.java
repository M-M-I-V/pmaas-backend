package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.entity.AuditLog;
import dev.mmiv.pmaas.repository.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Centralised, tamper-evident audit logging service.
 * Hash chaining:
 *   Each entry stores a SHA-256 hash of its own field values concatenated with the
 *   previous entry's hash. The first entry uses "GENESIS" as its anchor.
 *   Any deletion, reordering, or field edit in the database breaks the chain.
 *   AuditIntegrityService (Phase 3 addition) can walk all records, recompute hashes,
 *   and alert when a discrepancy is found.
 * Single access point:
 *   AuditLogController previously injected AuditLogRepository directly. All reads
 *   now go through getLogsForRecord() defined here so any future access controls,
 *   pagination limits, or response-shaping logic apply uniformly.
 * Audit event coverage (complete list):
 *   AUTH       — LOGIN, LOGIN_FAILED, LOGOUT, RATE_LIMITED
 *   Patients   — CREATE, UPDATE, DELETE
 *   MedicalVisits — CREATE, UPDATE, DELETE, READ (sensitive record access)
 *   DentalVisits  — CREATE, UPDATE, DELETE, READ
 * USAGE — call from service layer, never from controllers:
 *   auditLogService.record("Patients", patient.getId(), "CREATE", "Patient created");
 *   auditLogService.record("AUTH",     0,               "LOGIN",  "IP: 192.168.1.1");
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // Write API

    /**
     * Appends an immutable, hash-chained audit entry.
     * This method is intentionally fail-safe: if the audit write fails (e.g. DB
     * is temporarily unavailable), the failure is logged loudly but never thrown
     * to the caller. The main business operation must not be aborted because of
     * an audit failure — that would create a denial-of-service vector.
     * @param entityName  The entity type affected ("Patients", "AUTH", etc.)
     * @param recordId    The PK of the affected row; use 0 for non-row events.
     * @param action      CREATE | READ | UPDATE | DELETE | LOGIN | LOGIN_FAILED | LOGOUT
     * @param details     Brief, human-readable context. Never include passwords or tokens.
     */
    public void record(
        String entityName,
        int recordId,
        String action,
        String details
    ) {
        String username = resolveUsername();
        LocalDateTime ts = LocalDateTime.now();

        String prevHash = auditLogRepository
            .findTopByOrderByIdDesc()
            .map(AuditLog::getHash)
            .orElse("GENESIS");

        String hash = computeChainHash(
            entityName,
            recordId,
            action,
            username,
            ts,
            details,
            prevHash
        );

        AuditLog entry = AuditLog.builder()
            .entityName(entityName)
            .recordId(recordId)
            .action(action)
            .username(username)
            .timestamp(ts)
            .details(details)
            .hash(hash)
            .build();

        try {
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // Loud logging — this must be investigated but must not crash the caller.
            log.error(
                "AUDIT WRITE FAILURE — entity={} id={} action={} user={}",
                entityName,
                recordId,
                action,
                username,
                ex
            );
        }
    }

    // Read API

    /**
     * Returns all audit entries for a specific record, newest first.
     * AuditLogController uses this instead of injecting the repository directly.
     */
    public List<AuditLog> getLogsForRecord(String entityName, int recordId) {
        return auditLogRepository.findByEntityNameAndRecordIdOrderByTimestampDesc(
            entityName,
            recordId
        );
    }

    // Private helpers

    /**
     * Resolves the currently authenticated username from the SecurityContext.
     * Returns "SYSTEM" for automated/non-interactive threads.
     * Returns "ANONYMOUS" if called before authentication completes (should not happen
     * on any secured endpoint, but is defensive for edge cases).
     */
    private String resolveUsername() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (
                auth != null &&
                auth.isAuthenticated() &&
                !"anonymousUser".equals(auth.getPrincipal())
            ) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // SecurityContext may be unavailable in async threads.
        }
        return "SYSTEM";
    }

    /**
     * Computes SHA-256(entityName|recordId|action|username|timestamp|details|prevHash).
     * The pipe character '|' is the field delimiter. Fields are concatenated in a
     * deterministic order so the hash can be independently reproduced for verification.
     * Null details are normalised to an empty string before hashing.
     * SHA-256 is guaranteed available by the Java specification (JCA mandatory algorithms),
     * so NoSuchAlgorithmException is wrapped in an IllegalStateException rather than
     * being declared as a checked exception.
     */
    private String computeChainHash(
        String entityName,
        int recordId,
        String action,
        String username,
        LocalDateTime timestamp,
        String details,
        String prevHash
    ) {
        String payload = String.join(
            "|",
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
            byte[] hashBytes = digest.digest(
                payload.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                "SHA-256 not available — JVM is non-conformant",
                ex
            );
        }
    }
}
