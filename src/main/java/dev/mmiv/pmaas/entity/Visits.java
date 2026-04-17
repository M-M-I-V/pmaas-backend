package dev.mmiv.pmaas.entity;

import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.entity.VisitType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Base visit entity — updated with multi-step workflow fields.
 *
 * ADDED FIELDS (via V8 migration):
 *   status              — current workflow state
 *   assignedToUserId    — user ID of the MD/DMD currently assigned
 *   assignedBy          — username of the nurse who assigned
 *   assignedAt          — when assignment was made
 *   completedAt         — when the visit reached COMPLETED
 *
 * Unchanged fields from the original entity are preserved below.
 * Using JOINED inheritance so medical_visits and dental_visits remain
 * separate tables with only their type-specific columns.
 */
@Entity
@Table(
        name = "visits",
        indexes = {
                @Index(name = "idx_visits_status",               columnList = "status"),
                @Index(name = "idx_visits_assigned_to_user_id",  columnList = "assigned_to_user_id"),
                @Index(name = "idx_visits_patient_id",           columnList = "patient_id"),
                @Index(name = "idx_visits_visit_date",           columnList = "visit_date")
        }
)
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "visit_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
public abstract class Visits {

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

    // Common vital sign fields (captured by nurse during CREATED_BY_NURSE)

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

    // Workflow fields (added by V8 migration)

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
     * Preserved even if re-assigned (most recent assignment).
     */
    @Column(name = "assigned_by", length = 255)
    private String assignedBy;

    /** Timestamp of the most recent assignment action. */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** Timestamp when the visit transitioned to COMPLETED. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Audit timestamps

    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
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