package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a scheduled clinical appointment.
 *
 * This is intentionally separate from the Contact entity. Contacts are
 * communication log entries (phone calls, SMS follow-ups). Appointments
 * are scheduled clinical encounters with a specific date, time, and
 * visit type — they are the canonical source for appointment-related
 * dashboard metrics (daily appointment count, show-rate).
 *
 * PAPER FORM FIELDS → ENTITY MAPPING:
 *   Full Name         → fullName  (also derivable from patient if linked)
 *   Year & Section    → yearSection (denormalised — students only)
 *   Contact Number    → contactNumber
 *   For (MED/DENTAL)  → visitType
 *   Chief Complaint   → chiefComplaint
 *   Date              → appointmentDate
 *   Time              → appointmentTime
 *   Signature         → OMITTED (not needed for digital records)
 */
@Entity
@Table(
    name = "appointments",
    indexes = {
        @Index(name = "idx_appointments_date", columnList = "appointment_date"),
        @Index(name = "idx_appointments_status", columnList = "status"),
        @Index(name = "idx_appointments_patient_id", columnList = "patient_id"),
        @Index(
            name = "idx_appointments_date_type",
            columnList = "appointment_date, visit_type"
        ),
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Patient reference (optional) ──────────────────────────────────────────

    /**
     * Optional link to a registered patient record.
     * Null for walk-in or unregistered patients.
     * When set, fullName and yearSection on this entity still hold the
     * original form values — they are not replaced by patient record data.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patients patient;

    // ── Paper form fields ──────────────────────────────────────────────────────

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    /**
     * Student year and section (e.g. "BSIT 3-A").
     * Null or "N/A" for Faculty/Staff who have no section.
     */
    @Column(name = "year_section", length = 100)
    private String yearSection;

    @Column(name = "contact_number", length = 50)
    private String contactNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 20)
    private VisitType visitType;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time")
    private LocalTime appointmentTime;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    /**
     * Set when the patient shows up and a visit is created.
     * Enables the show-rate computation:
     *   show rate = appointments WHERE visit_id IS NOT NULL / total appointments
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id")
    private Visits visit;

    /**
     * Free-text notes (rescheduling reason, special instructions, etc.).
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ── Audit ─────────────────────────────────────────────────────────────────

    @Column(name = "created_by", nullable = false, length = 255)
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
            this.status = AppointmentStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
