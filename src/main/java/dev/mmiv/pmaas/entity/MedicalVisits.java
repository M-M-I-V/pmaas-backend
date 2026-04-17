package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Medical visit — JOINED subtype of Visits.
 *
 * CHANGED in V8 migration:
 *   - nursesNotes TEXT column DROPPED
 *   - NurseNote @OneToMany relationship added
 *
 * All other medical-specific fields are unchanged.
 * MD-owned fields are populated only during PENDING_MD_REVIEW by the assigned MD.
 */
@Entity
@Table(name = "medical_visits")
@DiscriminatorValue("MEDICAL")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
public class MedicalVisits extends Visits {

    // NURSE-owned fields (set during CREATED_BY_NURSE)

    @Column(name = "weight", length = 20)
    private String weight;

    @Column(name = "height", length = 20)
    private String height;

    // MD-owned fields (set during PENDING_MD_REVIEW by assigned MD)

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

    @Column(name = "hama", columnDefinition = "TEXT")
    private String hama;

    /** Blob storage path for the referral form document. */
    @Column(name = "referral_form", length = 512)
    private String referralForm;

    /** Blob storage path for diagnostic test image. */
    @Column(name = "diagnostic_test_image", length = 512)
    private String diagnosticTestImage;

    /** Blob storage path for medical chart image. */
    @Column(name = "medical_chart_image", length = 512)
    private String medicalChartImage;

    // NurseNote relationship (replaces nursesNotes TEXT)

    /**
     * Append-only list of timestamped nurse notes.
     * Notes are added during PENDING_NURSE_REVIEW (first note → COMPLETED)
     * or on already-COMPLETED visits.
     *
     * @see NurseNote — immutable entity, never updated
     */
    @OneToMany(
            mappedBy = "visit",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = false
    )
    @OrderBy("createdAt ASC")
    private List<NurseNote> nurseNotes = new ArrayList<>();

    /**
     * Returns an unmodifiable view of nurse notes.
     * Use addNurseNote() to append — direct list manipulation is blocked.
     */
    public List<NurseNote> getNurseNotes() {
        return Collections.unmodifiableList(nurseNotes);
    }

    /** Package-visible appender used only by VisitWorkflowService. */
    public void addNurseNote(NurseNote note) {
        nurseNotes.add(note);
    }
}