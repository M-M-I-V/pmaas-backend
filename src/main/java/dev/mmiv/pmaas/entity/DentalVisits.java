package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Dental visit — JOINED subtype of Visits.
 *
 * Dental workflow (2 steps):
 *   CREATED_BY_NURSE → PENDING_DMD_REVIEW → COMPLETED
 *
 * Unlike medical visits, dental visits do NOT have a PENDING_NURSE_REVIEW
 * step and do NOT have nurse notes. The workflow ends when the DMD completes
 * the dental section.
 *
 * V13 CHANGE: The previously dental-specific fields (dentalNotes,
 * treatmentProvided, toothInvolved, diagnosis, plan, referralForm) were
 * either promoted to the base Visits entity or merged into tooth_status.
 *
 * Field mapping after V13:
 *   OLD field              → NEW location
 *   dental_notes           → visits.history         (DMD observations)
 *   treatment_provided     → visits.treatment        (treatment provided)
 *   tooth_involved         → dental_visits.tooth_status (merged with V1 column)
 *   diagnosis, plan        → visits.diagnosis, visits.plan
 *   referral_form          → visits.referral_form
 *
 * To access clinical section data (diagnosis, treatment, history, etc.),
 * use the inherited getters from Visits: getDiagnosis(), getTreatment(),
 * getHistory(), getPlan(), getReferralForm(), etc.
 *
 * This entity now holds only the fields that are genuinely dental-specific:
 *   - toothStatus: the tooth or teeth involved in the treatment
 *   - dentalChartImage: DMD-uploaded dental chart or X-ray image
 */
@Entity
@Table(name = "dental_visits")
@DiscriminatorValue("DENTAL")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
public class DentalVisits extends Visits {

    // ── STEP 3b — DMD-specific fields ────────────────────────────────────────
    //
    // All shared clinical fields (diagnosis, plan, treatment/treatment provided,
    // history/dental notes, referral form) are inherited from the base Visits
    // entity and accessed via getDiagnosis(), getTreatment(), getHistory(), etc.

    /**
     * Tooth or teeth involved in the diagnosis and treatment.
     * Maps to dental_visits.tooth_status (original V1 column, preserved).
     * In V9 this was called tooth_involved — V13 merged it back into tooth_status.
     *
     * Examples: "Upper left molar", "11, 12, 21", "Mandibular incisors"
     */
    @Column(name = "tooth_status", length = 500)
    private String toothStatus;

    /** Blob storage path for the DMD-uploaded dental chart or X-ray image. */
    @Column(name = "dental_chart_image", length = 512)
    private String dentalChartImage;
}
