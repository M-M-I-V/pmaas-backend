package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a single entry in the Contacts Log — a record of communication
 * between clinic staff and a patient, guardian, or external party.
 *
 * Maps directly to the "Contacts Log 2025" Excel sheet columns:
 *
 *   Date | Time | Name | Designation | Medical/Dental |
 *   Number | Mode of Comm | Purpose | Remarks | Respond
 *
 * patient_id is nullable to support walk-in contacts and guardians
 * who may not have a patient record in the system.
 */
@Entity
@Table(
    name = "contacts",
    indexes = {
        @Index(name = "idx_contacts_date", columnList = "contact_date"),
        @Index(name = "idx_contacts_name", columnList = "name"),
        @Index(name = "idx_contacts_visit_type", columnList = "visit_type"),
        @Index(
            name = "idx_contacts_mode",
            columnList = "mode_of_communication"
        ),
        @Index(name = "idx_contacts_respond", columnList = "respond"),
        @Index(name = "idx_contacts_patient_id", columnList = "patient_id"),
        @Index(
            name = "idx_contacts_date_name_number",
            columnList = "contact_date, name, contact_number"
        ),
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Date the contact was made. Indexed for date-range filtering. */
    @Column(name = "contact_date", nullable = false)
    private LocalDate contactDate;

    /** Time the contact was made. */
    @Column(name = "contact_time")
    private LocalTime contactTime;

    /** Full name of the patient, guardian, or external party contacted. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Role or designation of the person (e.g., Student, Faculty, Staff, Guardian).
     * Free text to accommodate the variety present in the existing logbook.
     */
    @Column(name = "designation", length = 100)
    private String designation;

    /** Whether the contact relates to a medical or dental concern. */
    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", length = 20)
    private VisitType visitType;

    /** Contact phone number. Stored as text to preserve leading zeros. */
    @Column(name = "contact_number", length = 50)
    private String contactNumber;

    /** How the communication was made (phone, SMS, email, etc.). */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_of_communication", length = 30)
    private ModeOfCommunication modeOfCommunication;

    /** Reason for the contact — reason the clinic reached out or was contacted. */
    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    /** Additional notes recorded by clinic staff during or after the contact. */
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /** Outcome of the contact attempt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "respond", length = 30)
    private Respond respond;

    /**
     * Link to the patient record.
     * Nullable — some contacts are for walk-ins or guardians not yet in the system.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patients patient;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
