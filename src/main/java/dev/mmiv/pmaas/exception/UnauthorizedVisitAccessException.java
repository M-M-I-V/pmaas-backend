package dev.mmiv.pmaas.exception;

public class UnauthorizedVisitAccessException extends VisitWorkflowException {

    private final Long visitId;
    private final Long assignedToUserId;
    private final Long requestingUserId;

    public UnauthorizedVisitAccessException(
        Long visitId,
        Long assignedToUserId,
        Long requestingUserId,
        String message
    ) {
        super(message);
        this.visitId = visitId;
        this.assignedToUserId = assignedToUserId;
        this.requestingUserId = requestingUserId;
    }

    public Long getVisitId() {
        return visitId;
    }

    public Long getAssignedToUserId() {
        return assignedToUserId;
    }

    public Long getRequestingUserId() {
        return requestingUserId;
    }
}
