package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.ModeOfCommunication;
import dev.mmiv.pmaas.entity.Respond;
import dev.mmiv.pmaas.entity.VisitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Inbound DTO for creating or updating a Contact.
 *
 * Using a dedicated DTO instead of accepting the entity directly prevents
 * mass-assignment vulnerabilities (id, createdAt, patient could be injected
 * if the entity were accepted in the request body).
 */
public record ContactRequest(
    @NotNull(message = "Contact date is required.")
    @PastOrPresent(message = "Contact date cannot be in the future.")
    LocalDate contactDate,

    LocalTime contactTime,

    @NotBlank(message = "Name is required.")
    @Size(max = 255, message = "Name must not exceed 255 characters.")
    String name,

    @Size(max = 100, message = "Designation must not exceed 100 characters.")
    String designation,

    VisitType visitType,

    @Pattern(
        regexp = "^[0-9+\\-\\s().]{0,50}$",
        message = "Contact number contains invalid characters."
    )
    String contactNumber,

    ModeOfCommunication modeOfCommunication,

    String purpose,

    String remarks,

    Respond respond,

    /** Optional — link this contact to an existing patient record by ID. */
    Long patientId
) {}
