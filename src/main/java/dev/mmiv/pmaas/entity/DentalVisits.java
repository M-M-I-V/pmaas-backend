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
 * No NurseNote relationship — the dental workflow has no PENDING_NURSE_REVIEW step.
 * All nurse-captured vitals and DMD clinical fields are stored directly here.
 */
@Entity
@Table(name = "dental_visits")
@DiscriminatorValue("DENTAL")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
public class DentalVisits extends Visits {

    // DMD-owned fields (set during PENDING_DMD_REVIEW)

    @Column(name = "dental_notes", columnDefinition = "TEXT")
    private String dentalNotes;

    @Column(name = "treatment_provided", columnDefinition = "TEXT")
    private String treatmentProvided;

    @Column(name = "tooth_involved", length = 100)
    private String toothInvolved;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "plan", columnDefinition = "TEXT")
    private String plan;

    @Column(name = "referral_form", length = 512)
    private String referralForm;

    /** Blob storage path for dental chart / X-ray image. */
    @Column(name = "dental_chart_image", length = 512)
    private String dentalChartImage;
}