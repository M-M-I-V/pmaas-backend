package dev.mmiv.pmaas.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exception handler for the Visit Workflow module.
 *
 * Add this as an @ExceptionHandler block inside your existing
 * GlobalExceptionHandler class, OR register this class as a
 * separate @RestControllerAdvice (both approaches work in Spring Boot).
 *
 * The response structure is consistent with the spec examples:
 * {
 *   "error":     "...",
 *   "message":   "...",
 *   "timestamp": "...",
 *   + optional context fields per exception type
 * }
 *
 * Error messages are intentionally vague on system internals —
 * no stack traces, class names, or SQL details leak to the client.
 */
@Slf4j
@RestControllerAdvice
public class VisitWorkflowExceptionHandler {

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(
            InvalidStateTransitionException ex) {

        log.warn("Invalid state transition: visitId={}, from={}, to={}",
                ex.getVisitId(), ex.getFrom(), ex.getTo());

        Map<String, Object> body = body("Invalid state transition", ex.getMessage());
        body.put("visitId",         ex.getVisitId());
        body.put("currentStatus",   ex.getFrom());
        body.put("attemptedStatus", ex.getTo());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(UnauthorizedVisitAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAccess(
            UnauthorizedVisitAccessException ex) {

        log.warn("Unauthorized visit access: visitId={}, assignedTo={}, requestedBy={}",
                ex.getVisitId(), ex.getAssignedToUserId(), ex.getRequestingUserId());

        Map<String, Object> body = body("Unauthorized", ex.getMessage());
        body.put("visitId",           ex.getVisitId());
        body.put("assignedToUserId",  ex.getAssignedToUserId());
        body.put("requestingUserId",  ex.getRequestingUserId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientInventory(
            InsufficientInventoryException ex) {

        log.warn("Insufficient inventory: item='{}', requested={}, available={}",
                ex.getItemName(), ex.getRequested(), ex.getAvailable());

        Map<String, Object> body = body("Insufficient inventory", ex.getMessage());
        body.put("inventoryItemId", ex.getInventoryItemId());
        body.put("itemName",        ex.getItemName());
        body.put("requested",       ex.getRequested());
        body.put("available",       ex.getAvailable());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePatientNotFound(
            PatientNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body("Not Found", ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(
            UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body("Not Found", ex.getMessage()));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Map<String, Object> body(String error, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("error",     error);
        map.put("message",   message);
        map.put("timestamp", LocalDateTime.now().toString());
        return map;
    }
}