package dev.mmiv.pmaas.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ImportResponse(
    String filename,
    String importedBy,
    LocalDateTime importedAt,
    int totalRowsProcessed,
    int successfulInserts,
    int skippedDuplicates,
    int failedRows,
    List<ImportRowError> errors
) {}