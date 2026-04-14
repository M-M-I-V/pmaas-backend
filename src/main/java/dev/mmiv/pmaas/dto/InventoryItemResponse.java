package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.InventoryItem;
import dev.mmiv.pmaas.entity.ItemCategory;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record InventoryItemResponse(
    Long id,
    String itemName,
    String brandName,
    ItemCategory category,
    String description,
    Integer stockOnHand,
    LocalDate expirationDate,
    LocalDate dateReceived,
    String remarks,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static InventoryItemResponse from(InventoryItem item) {
        return new InventoryItemResponse(
            item.getId(),
            item.getItemName(),
            item.getBrandName(),
            item.getCategory(),
            item.getDescription(),
            item.getStockOnHand(),
            item.getExpirationDate(),
            item.getDateReceived(),
            item.getRemarks(),
            item.getCreatedAt(),
            item.getUpdatedAt()
        );
    }
}