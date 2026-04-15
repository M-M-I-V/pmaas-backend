package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.Contact;
import dev.mmiv.pmaas.entity.ModeOfCommunication;
import dev.mmiv.pmaas.entity.Respond;
import dev.mmiv.pmaas.entity.VisitType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Outbound DTO for Contact responses.
 *
 * The patient association is flattened to patientId and patientName
 * to avoid serializing the full Patients entity graph (and triggering
 * a lazy load on every response).
 */
public record ContactResponse(
    Long id,
    LocalDate contactDate,
    LocalTime contactTime,
    String name,
    String designation,
    VisitType visitType,
    String contactNumber,
    ModeOfCommunication modeOfCommunication,
    String purpose,
    String remarks,
    Respond respond,
    int patientId,
    String patientName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * Static factory — converts a Contact entity to its response DTO.
     * Handles the nullable patient association safely.
     */
    public static ContactResponse from(Contact contact) {
        return new ContactResponse(
            contact.getId(),
            contact.getContactDate(),
            contact.getContactTime(),
            contact.getName(),
            contact.getDesignation(),
            contact.getVisitType(),
            contact.getContactNumber(),
            contact.getModeOfCommunication(),
            contact.getPurpose(),
            contact.getRemarks(),
            contact.getRespond(),
            contact.getPatient() != null ? contact.getPatient().getId() : null,
            contact.getPatient() != null
                ? contact.getPatient().getName()
                : null,
            contact.getCreatedAt(),
            contact.getUpdatedAt()
        );
    }
}
