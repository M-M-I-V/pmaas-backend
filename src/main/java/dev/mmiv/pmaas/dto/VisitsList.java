package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.VisitStatus;

import java.time.LocalDate;

/**
 * Lightweight visit summary DTO used by the visits table and the
 * per-patient visit history table in the frontend.
 *
 * CHANGES FROM OLD VERSION:
 *   - id: int → Long  (entity now uses Long primary key)
 *   - status: added  (required by the frontend to show workflow state badges
 *                     and conditionally enable Edit/Assign/Complete actions)
 *
 * JSON serialization: Long serializes as a number; JavaScript/TypeScript
 * handles int vs long transparently — no frontend change needed.
 *
 * Fields that are null for dental visits (physicalExamFindings, treatment)
 * are returned as null in the JSON. The frontend already handles nulls on
 * these columns since they were always optional.
 */
public record VisitsList(
        Long id,
        String fullName,
        LocalDate birthDate,
        LocalDate visitDate,
        String visitType,
        String chiefComplaint,
        String physicalExamFindings,
        String diagnosis,
        String treatment,
        VisitStatus status
) {}