package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A single timestamped, immutable nurse note attached to a medical visit.
 *
 * IMMUTABILITY CONTRACT:
 *   - No @Setter annotations — Lombok cannot generate mutators
 *   - No update endpoint exists in the API
 *   - @Column annotations set updatable = false on all fields
 *   - The only constructor is package-private (used by VisitWorkflowService)
 *   - Once persisted, a NurseNote record cannot be modified by any layer
 *
 * This mirrors the physical logbook behavior: a written note cannot be
 * erased — it must be superseded by a new note with a later timestamp.
 */
@Entity
@Table(
        name = "nurse_notes",
        indexes = {
                @Index(name = "idx_nurse_notes_visit_id", columnList = "visit_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA only
public class NurseNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false, updatable = false)
    private MedicalVisits visit;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    /**
     * Package-private factory constructor — only VisitWorkflowService
     * may create nurse notes. This prevents accidental instantiation
     * from controller or DTO layers.
     */
    public NurseNote(MedicalVisits visit, String content, String createdBy) {
        this.visit     = visit;
        this.content   = content;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }
}