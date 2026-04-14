package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "inventory_items",
    indexes = {
        @Index(name = "idx_inventory_item_name",   columnList = "item_name"),
        @Index(name = "idx_inventory_category",     columnList = "category"),
        @Index(name = "idx_inventory_expiration",   columnList = "expiration_date"),
        @Index(name = "idx_inventory_date_received",columnList = "date_received"),
        @Index(name = "idx_inventory_duplicate",    columnList = "item_name, date_received, category",
                unique = false)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "brand_name", length = 200)
    private String brandName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemCategory category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "stock_on_hand", nullable = false)
    private Integer stockOnHand;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "date_received")
    private LocalDate dateReceived;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}