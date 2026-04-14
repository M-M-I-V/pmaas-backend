package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.ItemCategory;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record InventoryItemRequest(

        @NotBlank(message = "Item name is required")
        @Size(max = 200, message = "Item name must be 200 characters or fewer")
        String itemName,

        @Size(max = 200, message = "Brand name must be 200 characters or fewer")
        String brandName,

        @NotNull(message = "Category is required")
        ItemCategory category,

        @Size(max = 2000, message = "Description must be 2000 characters or fewer")
        String description,

        @NotNull(message = "Stock on hand is required")
        @Min(value = 0, message = "Stock on hand cannot be negative")
        Integer stockOnHand,

        LocalDate expirationDate,
        LocalDate dateReceived,

        @Size(max = 1000, message = "Remarks must be 1000 characters or fewer")
        String remarks
) {}