package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Append-only, tamper-evident audit log record.
 * Three layers of immutability:
 *   1. @Setter REMOVED from class: no application code can call a setter after
 *      construction. The only way to populate fields is through @Builder.
 *   2. updatable = false on every @Column: even if a developer mistakenly
 *      obtains an instance and modifies a field in memory, Hibernate will never
 *      include those fields in an UPDATE statement. The database record is safe.
 *   3. hash field: each entry stores a SHA-256 hash of its own content plus the
 *      previous entry's hash. Any deletion, reordering, or field modification
 *      breaks the chain and is detectable by AuditIntegrityService.
 *      See AuditLogService.computeChainHash() for the exact payload format.
 * JPA requires a no-arg constructor — @NoArgsConstructor satisfies that contract
 * without opening a public setter surface.
 * DATABASE MIGRATION (run before deploying this change):
 *   ALTER TABLE audit_log ADD COLUMN hash VARCHAR(64);
 *   -- The column is nullable initially so existing rows are not rejected.
 *   -- New rows will always have a hash; old rows retain NULL.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Satisfies JPA, hides from public API
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Entity class / table name affected — e.g. "Patients", "MedicalVisits", "AUTH". */
    @Column(nullable = false, updatable = false)
    private String entityName;

    /** PK of the affected record; use 0 for non-record events (LOGIN, LOGOUT). */
    @Column(nullable = false, updatable = false)
    private int recordId;

    /** Action verb: CREATE | READ | UPDATE | DELETE | LOGIN | LOGIN_FAILED | LOGOUT | RATE_LIMITED */
    @Column(nullable = false, updatable = false)
    private String action;

    /** Authenticated username at the time of the event, or "SYSTEM" for automated operations. */
    @Column(nullable = false, updatable = false)
    private String username;

    /** Server-side timestamp recorded at the moment the event is persisted. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    /** Human-readable description. Never include passwords or tokens here. */
    @Column(columnDefinition = "TEXT", updatable = false)
    private String details;

    /**
     * SHA-256 hash of the concatenated fields of this entry plus the previous entry's hash.
     * The very first entry in the table uses the string "GENESIS" as the previous hash.
     * Stored as 64 hex characters (256 bits / 4 bits-per-hex-char).
     */
    @Column(length = 64, updatable = false)
    private String hash;
}
