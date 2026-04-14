package dev.mmiv.pmaas.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * Uniform error response body returned by GlobalExceptionHandler for all
 * error conditions. Every error the API emits has the same JSON shape,
 * making frontend error handling predictable and debuggable.
 *
 * Example response body for a 400 validation failure:
 * {
 *   "status":    400,
 *   "error":     "Bad Request",
 *   "message":   "Last name is required; Visit date must be in yyyy-MM-dd format",
 *   "path":      "/api/add-patient",
 *   "timestamp": "2026-03-18T10:30:00"
 * }
 *
 * IMPORTANT — the `message` field must never contain:
 *   - Stack traces or class names
 *   - Internal database error messages (these can reveal schema details)
 *   - Passwords, tokens, or any secret values
 * All internal details are logged server-side (see GlobalExceptionHandler).
 *
 * JACKSON NOTE:
 *
 * Jackson annotations remain in the {@code com.fasterxml.jackson.annotation}
 * package in both Jackson 2 and Jackson 3. The package name was intentionally
 * preserved to maintain backward compatibility across versions.
 *
 * DATA CONSISTENCY NOTE:
 * If {@code @JsonFormat} is not properly recognized by the configured
 * ObjectMapper (e.g., due to dependency version conflicts or configuration
 * issues), {@code LocalDateTime} values may be serialized using the default
 * numeric array representation:
 *
 *     [2026, 3, 18, 10, 30, 0]
 *
 * instead of the expected ISO-8601 string format:
 *
 *     "2026-03-18T10:30:00"
 *
 * This annotation enforces a predictable and standards-compliant date/time
 * serialization format, ensuring interoperability between services,
 * consistent API responses, and reliable client-side parsing.
 */
public record ErrorResponse(
    int status,
    String error,
    String message,
    String path,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timestamp
) {
    /** Convenience factory — avoids repeating LocalDateTime.now() at every call site. */
    public static ErrorResponse of(
        int status,
        String error,
        String message,
        String path
    ) {
        return new ErrorResponse(status, error, message, path, LocalDateTime.now());
    }
}