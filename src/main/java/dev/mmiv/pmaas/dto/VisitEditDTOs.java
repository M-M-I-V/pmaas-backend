package dev.mmiv.pmaas.dto;

/**
 * Edit request DTOs for medical and dental visits.
 *
 * DESIGN: Both DTOs contain all editable fields for their respective visit type.
 * The edit service (VisitEditService) applies only the subset of fields that the
 * authenticated user's role is permitted to modify in the current workflow status.
 *
 * This means the frontend sends one consistent payload regardless of role, and
 * the backend enforces field-level access control — the frontend never needs to
 * know which fields are locked for a given status/role combination.
 *
 * EDIT PERMISSION MATRIX:
 *
 * Medical visit:
 *   NURSE  + CREATED_BY_NURSE      → chiefComplaint, temperature, bloodPressure,
 *                                     pulseRate, respiratoryRate, spo2, weight, height
 *   MD     + PENDING_MD_REVIEW     → history, physicalExamFindings, diagnosis,
 *           + is assigned to visit    plan, treatment, diagnosticTestResult, hama,
 *                                     referralForm, diagnosticTestImage, medicalChartImage
 *   ANY    + COMPLETED             → read-only (use add-nurse-note for notes only)
 *
 * Dental visit:
 *   NURSE  + CREATED_BY_NURSE      → chiefComplaint, temperature, bloodPressure, pulseRate
 *   DMD    + PENDING_DMD_REVIEW    → dentalNotes, treatmentProvided, toothInvolved,
 *           + is assigned to visit    diagnosis, plan, referralForm, dentalChartImage
 *   ANY    + COMPLETED             → read-only
 */
public final class VisitEditDTOs {

    private VisitEditDTOs() {}

    /**
     * Medical visit edit request.
     * All fields are nullable — the service applies only the permitted subset.
     */
    public record MedicalVisitEditRequest(

            // ── NURSE-owned vitals ─────────────────────────────────────────────────
            String chiefComplaint,
            String temperature,
            String bloodPressure,
            String pulseRate,
            String respiratoryRate,
            String spo2,
            String weight,
            String height,

            // ── MD-owned clinical section ──────────────────────────────────────────
            String history,
            String physicalExamFindings,
            String diagnosis,
            String plan,
            String treatment,
            String diagnosticTestResult,
            String hama,
            String referralForm,
            String diagnosticTestImage,
            String medicalChartImage

    ) {}

    /**
     * Dental visit edit request.
     * All fields are nullable — the service applies only the permitted subset.
     */
    public record DentalVisitEditRequest(

            // ── NURSE-owned vitals ─────────────────────────────────────────────────
            String chiefComplaint,
            String temperature,
            String bloodPressure,
            String pulseRate,

            // ── DMD-owned clinical section ─────────────────────────────────────────
            String dentalNotes,
            String treatmentProvided,
            String toothInvolved,
            String diagnosis,
            String plan,
            String referralForm,
            String dentalChartImage

    ) {}
}