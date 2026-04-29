package dev.mmiv.pmaas.dto;

/**
 * Edit request DTOs for medical and dental visits.
 *
 * DESIGN: Both DTOs contain all editable fields for their respective visit type.
 * VisitEditService applies only the subset that the authenticated user's role
 * is permitted to modify in the current workflow status.
 *
 * V13 CHANGES — DentalVisitEditRequest:
 *   dentalNotes      → history       (maps to visits.history, base entity)
 *   treatmentProvided → treatment    (maps to visits.treatment, base entity)
 *   toothInvolved    → toothStatus   (maps to dental_visits.tooth_status)
 *   Added: physicalExamFindings, diagnosis, plan, referralForm to align with
 *   the base entity fields now available to DMD.
 *
 * EDIT PERMISSION MATRIX:
 *
 * Medical visit:
 *   NURSE  + CREATED_BY_NURSE      → chiefComplaint, temperature, bloodPressure,
 *                                     pulseRate, respiratoryRate, spo2
 *   MD     + PENDING_MD_REVIEW     → history, physicalExamFindings, diagnosis, plan,
 *           + is assigned to visit    treatment, diagnosticTestResult, diagnosticTestImage,
 *                                     hama, referralForm, medicalChartImage
 *   ANY    + COMPLETED             → read-only (use add-nurse-note for notes only)
 *
 * Dental visit:
 *   NURSE  + CREATED_BY_NURSE      → chiefComplaint, temperature, bloodPressure, pulseRate
 *   DMD    + PENDING_DMD_REVIEW    → history, physicalExamFindings, diagnosis, plan,
 *           + is assigned to visit    treatment, referralForm, toothStatus, dentalChartImage
 *   ANY    + COMPLETED             → read-only
 */
public final class VisitEditDTOs {

    private VisitEditDTOs() {}

    /**
     * Medical visit edit request.
     * All fields are nullable — VisitEditService applies only the permitted subset.
     */
    public record MedicalVisitEditRequest(
        // ── NURSE-owned vitals (base Visits entity) ───────────────────────
        String chiefComplaint,
        String temperature,
        String bloodPressure,
        String pulseRate,
        String respiratoryRate,
        String spo2,
        // ── MD-owned clinical section (base Visits entity after V13) ──────
        String history,
        String physicalExamFindings,
        String diagnosis,
        String plan,
        String treatment,
        String diagnosticTestResult,
        String hama,
        String referralForm,
        String diagnosticTestImage,
        // ── MD-owned (MedicalVisits entity) ──────────────────────────────
        String medicalChartImage
    ) {}

    /**
     * Dental visit edit request.
     * All fields are nullable — VisitEditService applies only the permitted subset.
     *
     * V13 FIELD CHANGES:
     *   history          replaces dentalNotes       (maps to visits.history)
     *   treatment        replaces treatmentProvided  (maps to visits.treatment)
     *   toothStatus      replaces toothInvolved      (maps to dental_visits.tooth_status)
     *   physicalExamFindings, diagnosis, plan, referralForm added as they are now
     *   accessible via the base entity and DMD may legitimately edit them.
     */
    public record DentalVisitEditRequest(
        // ── NURSE-owned vitals (base Visits entity) ───────────────────────
        String chiefComplaint,
        String temperature,
        String bloodPressure,
        String pulseRate,
        // ── DMD-owned clinical section (base Visits entity after V13) ─────
        String history,
        String physicalExamFindings,
        String diagnosis,
        String plan,
        String treatment,
        String referralForm,
        // ── DMD-owned (DentalVisits entity) ──────────────────────────────
        String toothStatus,
        String dentalChartImage
    ) {}
}
