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
 *
 * V13 CHANGES:
 *   - DentalVisitCompletionRequest: replaced dentalNotes/treatmentProvided/
 *     toothInvolved with fields that match the new base-entity layout.
 *     history replaces dentalNotes; treatment replaces treatmentProvided;
 *     toothStatus replaces toothInvolved.
 *   - DentalVisitResponse: updated to include full base clinical fields and
 *     dental-specific fields (toothStatus, dentalChartImage).
 *   - MedicalVisitResponse: added diagnosticTestImage,
 *     medicalChartImage which were missing from the previous version.
 *   - All other request/response records are unchanged.
 */
public final class VisitWorkflowDTOs {

    private VisitWorkflowDTOs() {}

    // ══════════════════════════════════════════════════════════════════════════
    // REQUEST DTOs
    // ══════════════════════════════════════════════════════════════════════════

    /** POST /api/visits/medical/create-initial — NURSE-owned fields only. */
    public record MedicalVisitCreateRequest(
        @NotNull(message = "Patient ID is required.") Long patientId,
        @NotNull(message = "Visit date is required.") LocalDate visitDate,
        @NotBlank(message = "Chief complaint is required.") @Size(
            max = 2000,
            message = "Chief complaint must not exceed 2000 characters."
        ) String chiefComplaint,
        String temperature,
        String bloodPressure,
        String pulseRate,
        String respiratoryRate,
        String spo2
    ) {}

    /** POST /api/visits/dental/create-initial — NURSE-owned fields only. */
    public record DentalVisitCreateRequest(
        @NotNull(message = "Patient ID is required.") Long patientId,
        @NotNull(message = "Visit date is required.") LocalDate visitDate,
        @NotBlank(message = "Chief complaint is required.") @Size(
            max = 2000
        ) String chiefComplaint,
        String temperature,
        String bloodPressure,
        String pulseRate
    ) {}

    /** POST /api/visits/{id}/assign — NURSE assigns to MD or DMD. */
    public record VisitAssignRequest(
        @NotNull(message = "Target user ID is required.") @Positive(
            message = "Target user ID must be positive."
        ) Long assignToUserId,
        @Size(
            max = 1000,
            message = "Comments must not exceed 1000 characters."
        ) String comments
    ) {}

    /**
     * PUT /api/visits/medical/{id}/complete-md-section — MD-owned fields only.
     *
     * All fields map to the base Visits entity after V13 except medicalChartImage
     * which maps to MedicalVisits.medicalChartImage.
     */
    public record MedicalVisitCompletionRequest(
        String history,
        String physicalExamFindings,
        @NotBlank(
            message = "Diagnosis is required to complete the medical section."
        ) String diagnosis,
        String plan,
        String treatment,
        String diagnosticTestResult,
        String hama,
        String referralForm,
        String diagnosticTestImage,
        String medicalChartImage
    ) {}

    /**
     * PUT /api/visits/dental/{id}/complete-dmd-section — DMD-owned fields only.
     *
     * V13 FIELD CHANGES:
     *   history      — DMD's clinical observations and notes.
     *                  Replaces the old "dentalNotes" field.
     *                  Maps to visits.history (base entity).
     *
     *   treatment    — Treatment provided by the DMD.
     *                  Replaces the old "treatmentProvided" field.
     *                  Maps to visits.treatment (base entity).
     *
     *   toothStatus  — Tooth or teeth involved in the treatment.
     *                  Replaces the old "toothInvolved" field.
     *                  Maps to dental_visits.tooth_status (DentalVisits entity).
     *
     *   physicalExamFindings — Clinical exam findings (optional for DMD).
     *                          Maps to visits.physical_exam_findings (base entity).
     *
     *   diagnosis, plan, referralForm, dentalChartImage — unchanged semantics,
     *   now stored in base visits table (except dentalChartImage → dental_visits).
     */
    public record DentalVisitCompletionRequest(
        @NotBlank(
            message = "Diagnosis is required to complete the dental section."
        ) String diagnosis,
        /**
         * DMD's clinical observations and notes about the dental condition.
         * Maps to visits.history (base entity). Was "dentalNotes" pre-V13.
         */
        String history,
        /**
         * Clinical examination findings (optional for dental visits).
         * Maps to visits.physical_exam_findings (base entity).
         */
        String physicalExamFindings,
        /**
         * Treatment provided by the DMD.
         * Maps to visits.treatment (base entity). Was "treatmentProvided" pre-V13.
         */
        String treatment,
        /**
         * Tooth or teeth involved in the diagnosis/treatment.
         * Maps to dental_visits.tooth_status (DentalVisits entity).
         * Was "toothInvolved" pre-V13.
         * Examples: "Upper left molar", "11, 12, 21"
         */
        String toothStatus,
        String plan,
        String referralForm,
        String dentalChartImage
    ) {}

    /** POST /api/visits/medical/{visitId}/add-nurse-note — NURSE adds a timestamped note. */
    public record NurseNoteRequest(
        @NotBlank(message = "Note content is required.") @Size(
            max = 5000,
            message = "Note content must not exceed 5000 characters."
        ) String content
    ) {}

    // Prescription request records

    /** POST /api/visits/{id}/prescribe — list of line items. */
    public record PrescriptionRequest(
        @NotNull(message = "Prescription list is required.") @Size(
            min = 1,
            message = "At least one prescription item is required."
        ) List<@Valid PrescriptionLineItem> prescriptions
    ) {}

    public record PrescriptionLineItem(
        @NotNull(message = "Inventory item ID is required.") @Positive(
            message = "Inventory item ID must be positive."
        ) Long inventoryItemId,
        @NotNull(message = "Quantity is required.") @Min(
            value = 1,
            message = "Quantity must be at least 1."
        ) Integer quantity,
        @Size(max = 1000) String reason
    ) {}

    // ══════════════════════════════════════════════════════════════════════════
    // RESPONSE DTOs
    // ══════════════════════════════════════════════════════════════════════════

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

    /**
     * Full medical visit response.
     * Returned by: MD section completion, nurse note addition, GET by ID, edit.
     *
     * V13 ADDITIONS:
     *   diagnosticTestImage       — now from base Visits entity
     *   medicalChartImage         — MD-uploaded chart (MedicalVisits-specific)
     */
    public record MedicalVisitResponse(
        Long visitId,
        VisitStatus status,
        Long patientId,
        String patientName,
        LocalDate visitDate,
        // Nurse vitals (base entity)
        String chiefComplaint,
        String temperature,
        String bloodPressure,
        String pulseRate,
        String respiratoryRate,
        String spo2,
        // MD clinical section (base entity — visits table after V13)
        String history,
        String physicalExamFindings,
        String diagnosis,
        String plan,
        String treatment,
        String diagnosticTestResult,
        String hama,
        String referralForm,
        String diagnosticTestImage,
        // Medical-specific (MedicalVisits — medical_visits table)
        String medicalChartImage,
        // Nurse notes (MedicalVisits only)
        List<NurseNoteResponse> nurseNotes,
        // Audit
        String createdBy,
        LocalDateTime createdAt,
        Long assignedToUserId,
        LocalDateTime assignedAt,
        LocalDateTime completedAt
    ) {}

    /**
     * Full dental visit response.
     * Returned by: DMD section completion, GET by ID, edit.
     *
     * V13 CHANGES:
     *   history           — DMD observations (was "dentalNotes")
     *   treatment         — treatment provided (was "treatmentProvided")
     *   toothStatus       — tooth(teeth) involved (was "toothInvolved")
     *   dentalChartImage  — added (was missing from previous response)
     *   physicalExamFindings — added (shared base field)
     */
    public record DentalVisitResponse(
        Long visitId,
        VisitStatus status,
        Long patientId,
        String patientName,
        LocalDate visitDate,
        // Nurse vitals (base entity)
        String chiefComplaint,
        String temperature,
        String bloodPressure,
        String pulseRate,
        // DMD clinical section (base entity — visits table after V13)
        String history,
        String physicalExamFindings,
        String diagnosis,
        String plan,
        String treatment,
        String referralForm,
        // Dental-specific (DentalVisits — dental_visits table)
        String toothStatus,
        String dentalChartImage,
        // Audit
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

    // Prescription response records

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
        String status // "SUCCESS" or "FAILED"
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
        // contactNumber intentionally omitted — callers must drill-down by ID
    ) {}
}
