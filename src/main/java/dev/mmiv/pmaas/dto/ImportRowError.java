package dev.mmiv.pmaas.dto;

public record ImportRowError(
        String sheet,
        int rowNumber,
        String field,
        String rejectedValue,
        String reason
) {}