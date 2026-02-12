package dev.mmiv.clinic.repository;

import dev.mmiv.clinic.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByEntityNameAndRecordIdOrderByTimestampDesc(String entityName, int entityId);
}
