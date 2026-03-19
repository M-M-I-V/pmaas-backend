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
 * Code-quality fix: `respiratoryRate` was declared `public` — the only public
 * instance field on any entity in the project. Public fields on JPA entities bypass
 * Lombok's access control (@Getter/@Setter) and allow any code to read or write
 * the field directly without going through the accessor layer. Changed to private;
 * Lombok's @Getter and @Setter on the class already generate the accessor methods.
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

    // CODE QUALITY FIX: was `public int respiratoryRate` — changed to private.
    // @Getter/@Setter on the class generate the public accessors; no direct
    // field access is needed or desirable on a JPA entity.
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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patients patient;
}
