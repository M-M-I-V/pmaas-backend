package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Base visit entity — JOINED inheritance strategy.
 *
 * V13 SCHEMA CHANGE: Clinical section fields promoted from subtype tables.
 *
 * Previously, fields like history, diagnosis, and plan lived in the
 * medical_visits and dental_visits subtype tables. This caused a critical
 * analytics bug: DashboardRepository's native SQL queries against
 * visits.diagnosis returned NULL for all rows because the column did not
 * exist on the base visits table.
 *
 * The clinical section fields are now in the base visits table so that:
 *   1. Analytics queries (top diagnoses, top complaints) work correctly.
 *   2. Both medical and dental workflows use the same field set — MDs fill
 *      them during PENDING_MD_REVIEW, DMDs fill them during PENDING_DMD_REVIEW.
 *   3. The JOINED inheritance JOIN query returns a complete record without
 *      requiring additional subtype JOINs for common clinical data.
 *
 * FIELD OWNERSHIP BY STEP:
 *
 *   STEP 1 — NURSE (all visit types):
 *     visitDate, chiefComplaint, temperature, bloodPressure,
 *     pulseRate, respiratoryRate, spo2, createdBy
 *
 *   STEP 2 — NURSE assigns (all visit types):
 *     status, assignedToUserId, assignedBy, assignedAt
 *
 *   STEP 3a — MD completes (MedicalVisits):
 *     history, physicalExamFindings, diagnosis, plan, treatment,
 *     diagnosticTestResult, diagnosticTestImage, hama, referralForm
 *     + MedicalVisits.medicalChartImage
 *
 *   STEP 3b — DMD completes (DentalVisits):
 *     history, diagnosis, plan, treatment, referralForm
 *     + DentalVisits.toothStatus, DentalVisits.dentalChartImage
 *
 *   STEP 4 — NURSE adds note (MedicalVisits only):
 *     MedicalVisits.nurseNotes → completedAt when first note added
 */
@Entity
@Table(
    name = "visits",
    indexes = {
        @Index(name = "idx_visits_status", columnList = "status"),
        @Index(
            name = "idx_visits_assigned_to_user_id",
            columnList = "assigned_to_user_id"
        ),
        @Index(name = "idx_visits_patient_id", columnList = "patient_id"),
        @Index(name = "idx_visits_visit_date", columnList = "visit_date"),
    }
)
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(
    name = "visit_type",
    discriminatorType = DiscriminatorType.STRING
)
@Getter
@Setter
public abstract class Visits {

    // ── STEP 1 — NURSE CREATES INITIAL VISIT ────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", insertable = false, updatable = false)
    private VisitType visitType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patients patient;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "temperature", length = 20)
    private String temperature;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    @Column(name = "pulse_rate", length = 20)
    private String pulseRate;

    @Column(name = "respiratory_rate", length = 20)
    private String respiratoryRate;

    @Column(name = "spo2", length = 20)
    private String spo2;

    // ── STEP 2 — NURSE ASSIGNS TO MD OR DMD ─────────────────────────────────

    /**
     * Current workflow status. Defaults to CREATED_BY_NURSE on insert.
     * Only VisitWorkflowService may change this field.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private VisitStatus status = VisitStatus.CREATED_BY_NURSE;

    /**
     * User ID of the MD or DMD currently assigned to this visit.
     * Null until NURSE assigns the visit.
     */
    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    /**
     * Username of the nurse who performed the assignment.
     * Preserved even if re-assigned (reflects the most recent assignment).
     */
    @Column(name = "assigned_by", length = 255)
    private String assignedBy;

    /** Timestamp of the most recent assignment action. */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** Timestamp when the visit transitioned to COMPLETED. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ── STEP 3a/3b — CLINICIAN COMPLETES SECTION ────────────────────────────
    //
    // These fields are shared between medical (MD) and dental (DMD) workflows.
    // Analytics queries (e.g. top diagnoses, top complaints) operate on the
    // base visits table, which is why these fields live here rather than in
    // the subtype tables. Promoted from medical_visits/dental_visits by V13.
    //
    // For dental visits the field semantics are:
    //   history               → DMD observations / dental notes
    //   treatment             → treatment provided by DMD
    //   physicalExamFindings  → clinical examination findings (optional for DMD)

    @Column(name = "history", columnDefinition = "TEXT")
    private String history;

    @Column(name = "physical_exam_findings", columnDefinition = "TEXT")
    private String physicalExamFindings;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "plan", columnDefinition = "TEXT")
    private String plan;

    @Column(name = "treatment", columnDefinition = "TEXT")
    private String treatment;

    @Column(name = "diagnostic_test_result", columnDefinition = "TEXT")
    private String diagnosticTestResult;

    /** Blob storage path for diagnostic test image (X-ray, scan, etc.). */
    @Column(name = "diagnostic_test_image", length = 512)
    private String diagnosticTestImage;

    /** HAMA assessment data. Primarily used for medical visits. */
    @Column(name = "hama", columnDefinition = "TEXT")
    private String hama;

    /** Blob storage path for referral form document. */
    @Column(name = "referral_form", length = 512)
    private String referralForm;

    // ── Audit timestamps ─────────────────────────────────────────────────────

    @Column(
        name = "created_by",
        nullable = false,
        updatable = false,
        length = 255
    )
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = VisitStatus.CREATED_BY_NURSE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
