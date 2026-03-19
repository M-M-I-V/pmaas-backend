package dev.mmiv.pmaas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * Uniform error response body returned by GlobalExceptionHandler for all
 * error conditions. Every error the API emits has the same JSON shape,
 * making frontend error handling predictable and debuggable.
 * Example response body for a 400 validation failure:
 * {
 *   "status":    400,
 *   "error":     "Bad Request",
 *   "message":   "Last name is required; Visit date must be in yyyy-MM-dd format",
 *   "path":      "/api/add-patient",
 *   "timestamp": "2026-03-18T10:30:00"
 * }
 * IMPORTANT — the `message` field must never contain:
 *   - Stack traces or class names
 *   - Internal database error messages (these can reveal schema details)
 *   - Passwords, tokens, or any secret values
 * All internal details are logged server-side (see GlobalExceptionHandler).
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime timestamp
) {
    /** Convenience factory — avoids repeating LocalDateTime.now() at every call site. */
    public static ErrorResponse of(
            int status,
            String error,
            String message,
            String path
    ) {
        return new ErrorResponse(
                status,
                error,
                message,
                path,
                LocalDateTime.now()
        );
    }
}