package dev.mmiv.pmaas.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base entity for all clinic visits. Extended by MedicalVisits and DentalVisits.
 *
 * CODE QUALITY FIX: `respiratoryRate` was declared `public` — the only public
 * instance field on any entity in the project. Changed to private.
 *
 * JACKSON NOTE:
 * Jackson annotations remain in the {@code com.fasterxml.jackson.annotation}
 * package in both Jackson 2 and Jackson 3. The package name was intentionally
 * preserved by FasterXML to maintain backward compatibility. There is no
 * {@code tools.jackson.annotation} package.
 *
 * SECURITY RISK:
 * If {@code @JsonIgnore} is not properly recognized by the configured
 * ObjectMapper (e.g., due to dependency mismatches or misconfiguration),
 * sensitive entity relationships may be serialized unintentionally.
 *
 * For example, the {@code patient} field could be included in API responses,
 * causing recursive traversal of the entire Patients graph (visits, records,
 * related entities). This may result in:
 *
 * - Unauthorized exposure of Protected Health Information (PHI)
 * - Excessive payload sizes and performance degradation
 * - Potential unbounded recursive serialization
 *
 * This annotation ensures that the field is excluded from JSON output,
 * preserving data privacy, response integrity, and system performance.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public abstract class Visits {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private LocalDate visitDate;

    @Enumerated(EnumType.STRING)
    private VisitType visitType;

    @Column(nullable = false)
    private String chiefComplaint;

    private Double temperature;
    private String bloodPressure;
    private int pulseRate;
    private int respiratoryRate;
    private Double spo2;

    @Column(columnDefinition = "TEXT")
    private String history;

    @Column(columnDefinition = "TEXT")
    private String physicalExamFindings;

    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    private String plan;

    @Column(columnDefinition = "TEXT")
    private String treatment;

    @Column(columnDefinition = "TEXT")
    private String diagnosticTestResult;

    private String diagnosticTestImage;

    // @JsonIgnore uses tools.jackson.annotation (Jackson 3) intentionally.
    // See class Javadoc for the full explanation of why this matters.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patients patient;
}