package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.NurseNote;
import dev.mmiv.pmaas.entity.VisitStatus;
import dev.mmiv.pmaas.entity.VisitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * All DTO records for the Visit Workflow module.
 *
 * Grouped in one file per spec requirement.
 * Each record is a pure data carrier — field-restricted to its role/step context.
 */
public final class VisitWorkflowDTOs {

    private VisitWorkflowDTOs() {}

    // REQUEST DTOs

    /** POST /api/visits/medical/create-initial — NURSE-owned fields only. */
    public record MedicalVisitCreateRequest(

            @NotNull(message = "Patient ID is required.")
            Long patientId,

            @NotNull(message = "Visit date is required.")
            LocalDate visitDate,

            @NotBlank(message = "Chief complaint is required.")
            @Size(max = 2000, message = "Chief complaint must not exceed 2000 characters.")
            String chiefComplaint,

            String temperature,
            String bloodPressure,
            String pulseRate,
            String respiratoryRate,
            String spo2,
            String weight,
            String height

    ) {}

    /** POST /api/visits/dental/create-initial — NURSE-owned fields only. */
    public record DentalVisitCreateRequest(

            @NotNull(message = "Patient ID is required.")
            Long patientId,

            @NotNull(message = "Visit date is required.")
            LocalDate visitDate,

            @NotBlank(message = "Chief complaint is required.")
            @Size(max = 2000)
            String chiefComplaint,

            String temperature,
            String bloodPressure,
            String pulseRate

    ) {}

    /** POST /api/visits/{id}/assign — used by NURSE to assign to MD or DMD. */
    public record VisitAssignRequest(

            @NotNull(message = "Target user ID is required.")
            @Positive(message = "Target user ID must be positive.")
            Long assignToUserId,

            @Size(max = 1000, message = "Comments must not exceed 1000 characters.")
            String comments

    ) {}

    /** PUT /api/visits/medical/{id}/complete-md-section — MD-owned fields only. */
    public record MedicalVisitCompletionRequest(

            String history,
            String physicalExamFindings,

            @NotBlank(message = "Diagnosis is required to complete the medical section.")
            String diagnosis,

            String plan,
            String treatment,
            String diagnosticTestResult,
            String hama,
            String referralForm,
            String diagnosticTestImage,
            String medicalChartImage

    ) {}

    /** PUT /api/visits/dental/{id}/complete-dmd-section — DMD-owned fields only. */
    public record DentalVisitCompletionRequest(

            @NotBlank(message = "Diagnosis is required to complete the dental section.")
            String diagnosis,

            String dentalNotes,
            String treatmentProvided,
            String toothInvolved,
            String plan,
            String referralForm,
            String dentalChartImage

    ) {}

    /** POST /api/visits/medical/{id}/add-nurse-note — NURSE adds a timestamped note. */
    public record NurseNoteRequest(

            @NotBlank(message = "Note content is required.")
            @Size(max = 5000, message = "Note content must not exceed 5000 characters.")
            String content

    ) {}

    // Prescription requests

    /**
     * POST /api/visits/{id}/prescribe — body contains a list of line items.
     */
    public record PrescriptionRequest(

            @NotNull(message = "Prescription list is required.")
            @Size(min = 1, message = "At least one prescription item is required.")
            List<@Valid PrescriptionLineItem> prescriptions

    ) {}

    public record PrescriptionLineItem(

            @NotNull(message = "Inventory item ID is required.")
            @Positive(message = "Inventory item ID must be positive.")
            Long inventoryItemId,

            @NotNull(message = "Quantity is required.")
            @Min(value = 1, message = "Quantity must be at least 1.")
            Integer quantity,

            @Size(max = 1000)
            String reason

    ) {}

    // RESPONSE DTOs

    /**
     * Lightweight response returned after visit creation or assignment.
     * Contains only workflow-relevant fields — no clinical details.
     */
    public record VisitSummaryResponse(
            Long visitId,
            VisitStatus status,
            VisitType visitType,
            Long patientId,
            String patientName,
            Long assignedToUserId,
            String assignedBy,
            LocalDateTime assignedAt,
            String createdBy,
            LocalDateTime createdAt
    ) {}

    /** Full medical visit response — returned after MD completion or note addition. */
    public record MedicalVisitResponse(
            Long visitId,
            VisitStatus status,
            Long patientId,
            String patientName,
            LocalDate visitDate,
            // Nurse vitals
            String chiefComplaint,
            String temperature,
            String bloodPressure,
            String pulseRate,
            String respiratoryRate,
            String spo2,
            // MD clinical section
            String history,
            String physicalExamFindings,
            String diagnosis,
            String plan,
            String treatment,
            String diagnosticTestResult,
            String hama,
            String referralForm,
            // Nurse notes
            List<NurseNoteResponse> nurseNotes,
            // Audit
            String createdBy,
            LocalDateTime createdAt,
            Long assignedToUserId,
            LocalDateTime assignedAt,
            LocalDateTime completedAt
    ) {}

    /** Full dental visit response — returned after DMD completion. */
    public record DentalVisitResponse(
            Long visitId,
            VisitStatus status,
            Long patientId,
            String patientName,
            LocalDate visitDate,
            String chiefComplaint,
            String temperature,
            String bloodPressure,
            String pulseRate,
            String diagnosis,
            String dentalNotes,
            String treatmentProvided,
            String toothInvolved,
            String plan,
            String referralForm,
            String createdBy,
            LocalDateTime createdAt,
            Long assignedToUserId,
            LocalDateTime assignedAt,
            LocalDateTime completedAt
    ) {}

    /** Single nurse note in a response — no PII, immutable record. */
    public record NurseNoteResponse(
            Long id,
            String content,
            LocalDateTime createdAt,
            String createdBy
    ) {
        public static NurseNoteResponse from(NurseNote note) {
            return new NurseNoteResponse(
                    note.getId(),
                    note.getContent(),
                    note.getCreatedAt(),
                    note.getCreatedBy()
            );
        }
    }

    // Prescription responses

    public record PrescriptionResultResponse(
            Long visitId,
            List<PrescriptionLineResult> prescriptions,
            String message,
            LocalDateTime prescribedAt,
            String prescribedBy
    ) {}

    public record PrescriptionLineResult(
            Long inventoryItemId,
            String itemName,
            Integer quantity,
            Integer previousStock,
            Integer newStock,
            String status  // "SUCCESS" or "FAILED"
    ) {}

    // Patient search response

    public record PatientSearchItem(
            Long id,
            String firstName,
            String lastName,
            String studentNumber,
            String birthDate,
            String category,
            String status
            // contactNumber intentionally omitted — caller must drill-down by ID
            // if they need contact info, keeping this response privacy-safe
    ) {}
}