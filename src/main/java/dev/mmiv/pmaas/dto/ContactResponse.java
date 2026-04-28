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
 * FIX: patientId changed from primitive `int` to boxed `Integer`.
 *
 * Root cause of the NPE:
 *   The record previously declared `int patientId` (primitive).
 *   When a contact has no linked patient, `contact.getPatient()` is null and
 *   `from()` returns `null` for that position. Java's record canonical constructor
 *   attempts to unbox null → int, which throws:
 *     NullPointerException: Cannot invoke "java.lang.Integer.intValue()"
 *   Using `Integer` (boxed) allows the field to be null safely.
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
        Integer patientId,      // FIX: was `int` — primitive cannot hold null
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