package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AuditLog.
 * Intentionally exposes no delete() or custom update methods.
 * All writes go through AuditLogService.record() only.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityNameAndRecordIdOrderByTimestampDesc(String entityName, int recordId);

    /**
     * Fetches the most recently saved audit entry.
     * Used by AuditLogService to retrieve the previous hash for chain linking.
     */
    Optional<AuditLog> findTopByOrderByIdDesc();
}