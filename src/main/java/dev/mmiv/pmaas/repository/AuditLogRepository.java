package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.AuditLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read and append-only repository for AuditLog.
 * Deliberately exposes no delete() or custom update methods.
 * All writes go exclusively through AuditLogService.record().
 * Reads for the controller go through AuditLogService.getLogsForRecord().
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityNameAndRecordIdOrderByTimestampDesc(
        String entityName,
        int recordId
    );

    /**
     * Returns the most recently persisted audit entry.
     * Used by AuditLogService to retrieve the previous hash before writing a new entry,
     * extending the SHA-256 chain by one link.
     */
    Optional<AuditLog> findTopByOrderByIdDesc();
}
