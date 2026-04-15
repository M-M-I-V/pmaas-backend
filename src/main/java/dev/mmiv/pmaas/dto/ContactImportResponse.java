package dev.mmiv.pmaas.dto;

import java.util.List;

/**
 * Summary returned after a contacts Excel import.
 *
 * Tells the caller exactly how many rows were processed, how many
 * were successfully saved, how many were skipped, and why each
 * failure occurred (by row number).
 */
public record ContactImportResponse(
        int totalRows,
        int imported,
        int skipped,
        int failed,
        List<String> errors
) {}