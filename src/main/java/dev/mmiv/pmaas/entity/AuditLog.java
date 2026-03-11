package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit log record.
 * - @Setter removed: no code path can mutate a persisted record.
 * - All business columns are marked updatable = false: Hibernate will
 * never include them in an UPDATE statement even if accidentally modified.
 * - @AllArgsConstructor is PRIVATE: construction goes exclusively through
 * the @Builder so every field is set intentionally at creation time.
 * - A 'hash' field enables SHA-256 hash chaining (see AuditLogService):
 * each entry hashes its own content + the previous entry's hash,
 * making any deletion or modification of a record detectable.
 * JPA requires a no-arg constructor — @NoArgsConstructor(access = AccessLevel.PROTECTED)
 * satisfies that requirement without exposing it to the public API.
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

    /** The entity type that was affected, e.g. "Patients", "MedicalVisits", "AUTH". */
    @Column(nullable = false, updatable = false)
    private String entityName;

    /** The primary key of the affected record. 0 for non-record events (e.g. LOGIN). */
    @Column(nullable = false, updatable = false)
    private int recordId;

    /** The action performed: CREATE, READ, UPDATE, DELETE, LOGIN, LOGIN_FAILED, LOGOUT. */
    @Column(nullable = false, updatable = false)
    private String action;

    /** The authenticated username who performed the action, or "SYSTEM". */
    @Column(nullable = false, updatable = false)
    private String username;

    /** Server-side timestamp at the moment the event was recorded. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    /** Human-readable description of the change. May be null for access events. */
    @Column(columnDefinition = "TEXT", updatable = false)
    private String details;

    /**
     * SHA-256 hash of (entityName | recordId | action | username | timestamp | details | prevHash).
     * The first record in the chain uses "GENESIS" as prevHash.
     * Any gap, reordering, or modification of records breaks the chain.
     */
    @Column(length = 64, updatable = false)
    private String hash;
}