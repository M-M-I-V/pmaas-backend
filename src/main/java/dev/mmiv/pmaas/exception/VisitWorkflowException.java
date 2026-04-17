package dev.mmiv.pmaas.exception;

/**
 * Exception hierarchy for the Visit Workflow module.
 *
 * All exceptions are unchecked (extend RuntimeException) so they propagate
 * through service and controller layers to the GlobalExceptionHandler without
 * requiring checked exception declarations throughout the call stack.
 *
 * The GlobalExceptionHandler maps each subtype to the appropriate HTTP status:
 *   InvalidStateTransitionException     → 400 Bad Request
 *   UnauthorizedVisitAccessException    → 403 Forbidden
 *   InsufficientInventoryException      → 400 Bad Request
 *   PatientNotFoundException            → 404 Not Found
 *   UserNotFoundException               → 404 Not Found
 */

// Base
class VisitWorkflowException extends RuntimeException {

    VisitWorkflowException(String message) {
        super(message);
    }
}
