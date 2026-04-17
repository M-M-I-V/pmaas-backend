package dev.mmiv.pmaas.exception;

public class PatientNotFoundException extends VisitWorkflowException {

    public PatientNotFoundException(Long patientId) {
        super("Patient with id " + patientId + " not found.");
    }
}
