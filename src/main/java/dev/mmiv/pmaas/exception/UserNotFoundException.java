package dev.mmiv.pmaas.exception;

public class UserNotFoundException extends VisitWorkflowException {

    public UserNotFoundException(Long userId) {
        super("User with id " + userId + " not found.");
    }
}
