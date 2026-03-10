package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityNameAndRecordIdOrderByTimestampDesc(String entityName, int entityId);
}
