package dev.mmiv.pmaas.exception;

import dev.mmiv.pmaas.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * Centralised, consistent error handling for all controllers.
 *  Every exception the API can throw is handled here and returns a structured
 *  ErrorResponse { status, error, message, path, timestamp }. Internal details
 *  (stack traces, SQL errors, class names) are logged server-side only and never
 *  returned to the client.
 * getReason() was deprecated in Spring Framework 6 in favour of
 *  the RFC 7807 Problem Details API. This handler uses getBody().getDetail() which
 *  is the non-deprecated equivalent. The fall-through chain:
 *      1. ex.getBody().getDetail()  — set when the exception is constructed with a reason
 *      2. "Request error"           — safe fallback for exceptions with no detail
 * Handler priority (most specific → least specific):
 *   1. MethodArgumentNotValidException   — @Valid failures (400)
 *   2. ResponseStatusException           — explicit 4xx/5xx from service layer
 *   3. MaxUploadSizeExceededException    — file too large (413)
 *   4. AccessDeniedException             — @PreAuthorize rejection (403)
 *   5. Exception                         — catch-all (500, never exposes internals)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles @Valid failures on @RequestBody / @ModelAttribute parameters.
     * Collects ALL constraint violation messages into one readable string so the
     * frontend can display each invalid field without making multiple round-trips.
     * Example: "lastName: Last name is required; visitDate: Must be yyyy-MM-dd"
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("Validation failure at {}: {}", request.getRequestURI(), message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Bad Request", message, request.getRequestURI()));
    }

    /**
     * Handles ResponseStatusException thrown by service and controller code.
     * DEPRECATION FIX: replaced ex.getReason() with ex.getBody().getDetail().
     * In Spring Framework 6+, ResponseStatusException extends ErrorResponseException
     * which holds a ProblemDetail body. The reason/detail is accessed via getBody().
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {

        log.warn("ResponseStatusException at {}: {}", request.getRequestURI(), ex.getMessage());

        int statusCode = ex.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(statusCode); // Extract to variable

        // Null-safe check using the variable
        String error = (status != null) ? status.getReasonPhrase() : ex.getStatusCode().toString();

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(ErrorResponse.of(
                        statusCode,
                        error,
                        ex.getBody().getDetail() != null ? ex.getBody().getDetail() : "Request error",
                        request.getRequestURI()
                ));
    }

    /**
     * Handles multipart file size limit exceeded.
     * Triggered when a request body exceeds spring.servlet.multipart.max-file-size.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        log.warn("File size limit exceeded at {}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.CONTENT_TOO_LARGE) // Updated enum constant
                .body(ErrorResponse.of(
                        413,
                        "Content Too Large", // Updated reason phrase string
                        "The uploaded file exceeds the maximum permitted size of 10 MB.",
                        request.getRequestURI()
                ));
    }

    /**
     * Handles @PreAuthorize / method security rejections.
     * NOTE: Spring Security's own AccessDeniedHandler intercepts most cases before
     * they reach this advice. This acts as a safety net for AccessDeniedException
     * thrown manually inside service methods or in edge-case filter interactions.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("Access denied at {} — insufficient role", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        403,
                        "Forbidden",
                        "You do not have permission to perform this action.",
                        request.getRequestURI()
                ));
    }

    /**
     * Catch-all for any exception not explicitly handled above.
     * SECURITY: The raw exception message is NEVER returned to the client.
     * SQL errors, class names, and stack traces are logged server-side only.
     * The client receives a generic 500 message — no implementation details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        500,
                        "Internal Server Error",
                        "An unexpected error occurred. Please try again or contact support.",
                        request.getRequestURI()
                ));
    }
}