package dev.mmiv.pmaas.entity;

import java.util.Arrays;
import java.util.Optional;

public enum ItemCategory {
    MEDICINE,
    SUPPLIES,
    CONSUMABLES;

    public static Optional<ItemCategory> fromSheetName(String sheetName) {
        if (sheetName == null) return Optional.empty();
        return Arrays.stream(values())
            .filter(c -> c.name().equalsIgnoreCase(sheetName.trim()))
            .findFirst();
    }
}