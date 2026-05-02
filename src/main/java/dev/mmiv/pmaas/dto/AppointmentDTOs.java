package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.AppointmentStatus;
import dev.mmiv.pmaas.entity.VisitType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * All Appointment module DTOs in one file.
 */
public final class AppointmentDTOs {

    private AppointmentDTOs() {}

    // ══════════════════════════════════════════════════════════════════════════
    // REQUEST DTOs
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/appointments — create a new appointment.
     *
     * patientId is optional. If provided, the appointment is linked to an
     * existing patient record. fullName and yearSection are always required
     * directly on the request to preserve the original form data.
     */
    public record AppointmentCreateRequest(
        /** Optional: link to an existing patient record. */
        Long patientId,
        @NotBlank(message = "Full name is required.") @Size(
            max = 255,
            message = "Full name must not exceed 255 characters."
        ) String fullName,
        /**
         * Year and section (e.g. "BSIT 3-A").
         * Pass null or "N/A" for non-student appointments.
         */
        @Size(
            max = 100,
            message = "Year/section must not exceed 100 characters."
        ) String yearSection,
        @Pattern(
            regexp = "^$|^[0-9+\\-\\s().]{0,50}$",
            message = "Contact number contains invalid characters."
        ) String contactNumber,
        @NotNull(message = "Visit type is required.") VisitType visitType,
        @Size(
            max = 2000,
            message = "Chief complaint must not exceed 2000 characters."
        ) String chiefComplaint,
        @NotNull(message = "Appointment date is required.") @FutureOrPresent(
            message = "Appointment date cannot be in the past."
        ) LocalDate appointmentDate,
        LocalTime appointmentTime,
        @Size(
            max = 2000,
            message = "Notes must not exceed 2000 characters."
        ) String notes
    ) {}

    /**
     * PUT /api/appointments/{id} — update an existing appointment.
     *
     * All fields are optional — null means "no change".
     * The service rejects updates to terminal-status appointments
     * (COMPLETED, CANCELLED, NO_SHOW).
     */
    public record AppointmentUpdateRequest(
        Long patientId,
        @Size(max = 255) String fullName,
        @Size(max = 100) String yearSection,
        @Pattern(
            regexp = "^$|^[0-9+\\-\\s().]{0,50}$",
            message = "Contact number contains invalid characters."
        ) String contactNumber,
        VisitType visitType,
        @Size(max = 2000) String chiefComplaint,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        @Size(max = 2000) String notes
    ) {}

    /**
     * POST /api/appointments/{id}/status — change lifecycle status.
     *
     * Used for:
     *   PENDING   → CONFIRMED  (staff confirms the slot)
     *   CONFIRMED → COMPLETED  (patient showed up — optionally link visitId)
     *   CONFIRMED → NO_SHOW    (patient did not appear)
     *   PENDING/CONFIRMED → CANCELLED
     *
     * visitId is optional. When transitioning to COMPLETED, supplying a
     * visitId links the appointment to the actual clinical visit record.
     */
    public record AppointmentStatusRequest(
        @NotNull(message = "New status is required.") AppointmentStatus status,
        /** Optional: visit record to link when marking COMPLETED. */
        Long visitId,
        @Size(max = 2000) String notes
    ) {}

    // ══════════════════════════════════════════════════════════════════════════
    // RESPONSE DTOs
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Full appointment detail response.
     * Returned by POST, PUT, GET by ID, and status change endpoints.
     */
    public record AppointmentResponse(
        Long id,
        Long patientId,
        String patientName, // from patient record if linked, else null
        String fullName, // from form
        String yearSection,
        String contactNumber,
        VisitType visitType,
        String chiefComplaint,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        AppointmentStatus status,
        Long visitId, // null until COMPLETED and linked
        String notes,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    /**
     * Lightweight appointment row for list/table views.
     * Excludes notes and audit timestamps to reduce payload size.
     */
    public record AppointmentListItem(
        Long id,
        String fullName,
        String yearSection,
        String contactNumber,
        VisitType visitType,
        String chiefComplaint,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        AppointmentStatus status,
        Long patientId,
        Long visitId
    ) {}

    /**
     * Daily appointment count — used by the dashboard chart.
     * Replaces DashboardDTOs.DailyAppointmentsDTO which was sourced
     * from the contacts table.
     */
    public record DailyAppointmentCountDTO(
        LocalDate date,
        Long totalCount,
        Long medicalCount,
        Long dentalCount
    ) {}

    /**
     * Monthly show-rate DTO sourced from the appointments table.
     * show rate = COMPLETED appointments / (non-CANCELLED appointments)
     */
    public record AppointmentShowRateDTO(
        LocalDate date,
        Long scheduledCount, // all non-CANCELLED
        Long completedCount, // COMPLETED (visit_id IS NOT NULL)
        Long noShowCount,
        Double showRatePercentage
    ) {}
}
