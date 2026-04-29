package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Medical visit — JOINED subtype of Visits.
 *
 * Medical workflow (3 steps):
 *   CREATED_BY_NURSE → PENDING_MD_REVIEW → PENDING_NURSE_REVIEW → COMPLETED
 *
 * V13 CHANGE: All shared clinical fields (history, physicalExamFindings,
 * diagnosis, plan, treatment, diagnosticTestResult, diagnosticTestImage,
 * hama, referralForm) were promoted to the base Visits entity. These fields
 * are now stored in the visits table and accessed via inherited getters/setters.
 *
 * This entity now holds only the fields that are genuinely medical-specific:
 *   - weight, height: nurse-captured vitals not applicable to dental
 *   - medicalChartImage: MD-uploaded chart image
 *   - nurseNotes: the append-only PENDING_NURSE_REVIEW note list (STEP 4)
 *
 * To access clinical section data (diagnosis, treatment, etc.), use the
 * inherited getters from Visits: getDiagnosis(), getTreatment(), etc.
 */
@Entity
@Table(name = "medical_visits")
@DiscriminatorValue("MEDICAL")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
public class MedicalVisits extends Visits {

    // ── STEP 3a — MD-specific fields ─────────────────────────────────────────
    //
    // All other MD clinical fields (history, diagnosis, plan, treatment, etc.)
    // are inherited from the base Visits entity. They live in the visits table
    // and are accessible via getHistory(), getDiagnosis(), etc.

    /** Blob storage path for the MD-uploaded medical chart image. */
    @Column(name = "medical_chart_image", length = 512)
    private String medicalChartImage;

    // ── STEP 4 — nurse notes (medical only) ──────────────────────────────────

    /**
     * Append-only list of timestamped nurse notes.
     * Notes are added during PENDING_NURSE_REVIEW (first note → COMPLETED)
     * or on already-COMPLETED visits.
     *
     * This relationship does NOT exist on DentalVisits — the dental workflow
     * ends at DMD completion (PENDING_DMD_REVIEW → COMPLETED) without a
     * PENDING_NURSE_REVIEW step.
     *
     * @see NurseNote — immutable entity, never updated after creation
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
     * Returns an unmodifiable view of the nurse notes list.
     * Prevents direct list manipulation from outside this class.
     * Use addNurseNote() to append — it is the only sanctioned write path.
     */
    public List<NurseNote> getNurseNotes() {
        return Collections.unmodifiableList(nurseNotes);
    }

    /**
     * Appends an immutable nurse note to this visit.
     * Called exclusively by VisitWorkflowService.addNurseNote().
     * The note must already be persisted before calling this method.
     */
    public void addNurseNote(NurseNote note) {
        nurseNotes.add(note);
    }
}
