package dev.mmiv.clinic.service;

import dev.mmiv.clinic.entity.AuditLog;
import dev.mmiv.clinic.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    public void record(String entityName, int recordId, String action, String details) {
        String username = "SYSTEM";
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();

                if (principal instanceof org.springframework.security.core.userdetails.User user) {
                    username = user.getUsername();
                } else if (principal instanceof String) {
                    username = (String) principal;
                } else {
                    username = authentication.getName();
                }
            }
        } catch (Exception ignored) {}

        AuditLog log = AuditLog.builder()
                .entityName(entityName)
                .recordId(recordId)
                .action(action)
                .username(username)
                .timestamp(LocalDateTime.now())
                .details(details)
                .build();

        auditLogRepository.save(log);
    }
}
