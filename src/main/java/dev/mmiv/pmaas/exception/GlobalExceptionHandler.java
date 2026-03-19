package dev.mmiv.pmaas.exception;

import dev.mmiv.pmaas.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Centralised exception handling for all controllers.
 *
 * Before this class, error handling was inconsistent:
 *   - Some controllers had try/catch returning raw e.getMessage() strings
 *   - Others let exceptions propagate, producing Spring's default HTML/JSON error page
 *   - MethodArgumentNotValidException (from @Valid) produced Spring's internal error format
 *   - No single place to ensure stack traces never leaked to clients
 *
 * Now every exception the API can throw is handled here and returns a structured
 * ErrorResponse with a safe, human-readable message. Internal details are logged
 * server-side at the appropriate level so nothing is lost for debugging.
 *
 * Handler priority (most specific → least specific):
 *   1. MethodArgumentNotValidException — @Valid failures (400)
 *   2. ResponseStatusException         — explicit 4xx/5xx from service layer
 *   3. MaxUploadSizeExceededException  — multipart size limit exceeded (413)
 *   4. AccessDeniedException           — @PreAuthorize rejection (403)
 *   5. Exception                       — catch-all (500, never exposes internals)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles @Valid failures on @RequestBody / @ModelAttribute parameters.
     * Collects all constraint violation messages into a single, readable string
     * so the frontend can display each broken field without making multiple
     * round-trips. Example: "Last name is required; Visit date must be yyyy-MM-dd"
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        String message = ex
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));

        log.warn(
            "Validation failure at {}: {}",
            request.getRequestURI(),
            message
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse.of(
                400,
                "Bad Request",
                message,
                request.getRequestURI()
            )
        );
    }

    /**
     * Handles ResponseStatusException thrown by service and controller code.
     * The message in the exception is developer-controlled and safe to surface.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
        ResponseStatusException ex,
        HttpServletRequest request
    ) {
        int statusCode = ex.getStatusCode().value();
        // Log 5xx as errors, 4xx as warnings — 4xx is expected (user error), 5xx is not
        if (statusCode >= 500) {
            log.error(
                "ResponseStatusException at {}: {}",
                request.getRequestURI(),
                ex.getReason(),
                ex
            );
        } else {
            log.warn(
                "ResponseStatusException at {}: {} {}",
                request.getRequestURI(),
                statusCode,
                ex.getReason()
            );
        }

        return ResponseEntity.status(ex.getStatusCode()).body(
            ErrorResponse.of(
                statusCode,
                ex.getStatusCode().toString(),
                ex.getReason() != null ? ex.getReason() : "Request error",
                request.getRequestURI()
            )
        );
    }

    /**
     * Handles multipart file size limit exceeded.
     * Triggered when a request body exceeds spring.servlet.multipart.max-file-size.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(
        MaxUploadSizeExceededException ex,
        HttpServletRequest request
    ) {
        log.warn("File size limit exceeded at {}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            ErrorResponse.of(
                413,
                "Payload Too Large",
                "File size exceeds the maximum allowed limit of 10MB.",
                request.getRequestURI()
            )
        );
    }

    /**
     * Handles @PreAuthorize / method security rejections.
     * NOTE: AccessDeniedException is normally handled by Spring Security's
     * AccessDeniedHandler before reaching this @RestControllerAdvice. This handler
     * acts as a safety net for cases where the exception bubbles past that layer
     * (e.g., thrown manually in a service method).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
        AccessDeniedException ex,
        HttpServletRequest request
    ) {
        log.warn(
            "Access denied at {} for user attempting forbidden operation",
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse.of(
                403,
                "Forbidden",
                "You do not have permission to perform this action.",
                request.getRequestURI()
            )
        );
    }

    /**
     * Catch-all for any exception not handled above.
     * SECURITY: The raw exception message is NEVER returned to the client.
     * Internal details (stack traces, SQL errors, class names) are logged
     * server-side only. The client receives a generic 500 message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
        Exception ex,
        HttpServletRequest request
    ) {
        // Log with full stack trace so it can be investigated
        log.error(
            "Unhandled exception at {}: {}",
            request.getRequestURI(),
            ex.getMessage(),
            ex
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse.of(
                500,
                "Internal Server Error",
                "An unexpected error occurred. Please try again or contact support.",
                request.getRequestURI()
            )
        );
    }
}
