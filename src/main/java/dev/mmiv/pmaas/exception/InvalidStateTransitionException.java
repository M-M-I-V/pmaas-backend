package dev.mmiv.pmaas.exception;

import dev.mmiv.pmaas.entity.VisitStatus;

/**
 * Exception thrown when an invalid state transition is attempted on a visit.
 *
 * Maps to 400 Bad Request in the GlobalExceptionHandler.
 */
public class InvalidStateTransitionException extends VisitWorkflowException {

    private final Long visitId;
    private final VisitStatus from;
    private final VisitStatus to;

    public InvalidStateTransitionException(
        Long visitId,
        VisitStatus from,
        VisitStatus to,
        String message
    ) {
        super(message);
        this.visitId = visitId;
        this.from = from;
        this.to = to;
    }

    public Long getVisitId() {
        return visitId;
    }

    public VisitStatus getFrom() {
        return from;
    }

    public VisitStatus getTo() {
        return to;
    }
}
