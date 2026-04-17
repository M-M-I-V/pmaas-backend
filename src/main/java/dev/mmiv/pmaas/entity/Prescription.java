package dev.mmiv.pmaas.entity;

import dev.mmiv.pmaas.entity.InventoryItem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An immutable prescription record created when MD/DMD deducts medicines
 * from inventory during or after a visit.
 *
 * One prescription row per medicine per visit — if a clinician prescribes
 * three medicines, three Prescription rows are created atomically.
 *
 * The quantity field reflects what was actually deducted from inventory.
 * The previousStock and newStock fields create a point-in-time audit trail
 * so inventory discrepancies can be traced to specific prescription events.
 */
@Entity
@Table(
        name = "prescriptions",
        indexes = {
                @Index(name = "idx_prescriptions_visit_id",      columnList = "visit_id"),
                @Index(name = "idx_prescriptions_prescribed_at", columnList = "prescribed_at"),
                @Index(name = "idx_prescriptions_item_id",       columnList = "inventory_item_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false, updatable = false)
    private Visits visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false, updatable = false)
    private InventoryItem inventoryItem;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    /** Stock count before deduction — for audit trail. */
    @Column(name = "previous_stock", nullable = false, updatable = false)
    private Integer previousStock;

    /** Stock count after deduction — for audit trail. */
    @Column(name = "new_stock", nullable = false, updatable = false)
    private Integer newStock;

    @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(name = "prescribed_at", nullable = false, updatable = false)
    private LocalDateTime prescribedAt;

    @Column(name = "prescribed_by", nullable = false, updatable = false, length = 255)
    private String prescribedBy;

    /**
     * Package-private factory constructor — only PrescriptionService
     * may create prescriptions, after validating stock and deducting inventory.
     */
    public Prescription(
            Visits visit,
            InventoryItem inventoryItem,
            int quantity,
            int previousStock,
            int newStock,
            String reason,
            String prescribedBy
    ) {
        this.visit         = visit;
        this.inventoryItem = inventoryItem;
        this.quantity      = quantity;
        this.previousStock = previousStock;
        this.newStock      = newStock;
        this.reason        = reason;
        this.prescribedBy  = prescribedBy;
        this.prescribedAt  = LocalDateTime.now();
    }
}