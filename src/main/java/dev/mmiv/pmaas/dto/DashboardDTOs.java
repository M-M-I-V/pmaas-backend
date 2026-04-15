package dev.mmiv.pmaas.dto;

import java.time.LocalDate;

/**
 * All Analytics Dashboard DTOs in one file per spec requirement.
 *
 * Each record is a pure data carrier — no behaviour, no PII.
 * Fields contain only aggregated metrics, date labels, and inventory
 * metadata. No patient names, contact numbers, or medical details.
 *
 * Records are immutable by design — Jackson serialises them without
 * @JsonProperty annotations since Java record accessor names match
 * the expected JSON camelCase keys.
 */
public final class DashboardDTOs {

    private DashboardDTOs() {
        // Utility class — not instantiable
    }

    // KPI DTOs

    /**
     * Total visit counts for dashboard header cards.
     * Returned by a single conditional-aggregation query to avoid
     * hitting the visits table twice per page load.
     */
    public record TotalVisitsKpiDTO(
            Long todayCount,
            Long monthToDateCount
    ) {}

    /**
     * Medical vs Dental split for the current month.
     * Used for the visit-type donut chart.
     */
    public record VisitRatioDTO(
            Long medicalCount,
            Long dentalCount
    ) {}

    /**
     * Count of patients flagged with a special medical condition
     * OR a communicable disease. Aggregated count only — no patient
     * identifiers are returned.
     */
    public record HighRiskPatientsKpiDTO(Long count) {}

    /**
     * Count of inventory items with zero stock.
     * Triggers the critical-stock alert badge.
     */
    public record CriticalInventoryKpiDTO(Long count) {}

    /**
     * Count of contacts logged for today.
     * Contacts represent scheduling and communication events.
     */
    public record AppointmentsTodayKpiDTO(Long count) {}

    // Clinical Analytics DTOs

    /**
     * A single diagnosis label and how many times it appears
     * in the filtered visit range. Top-10 results are returned.
     * No patient information is included.
     */
    public record TopDiagnosisDTO(
            String diagnosis,
            Long count
    ) {}

    /**
     * A single chief complaint label and its frequency.
     * Top-10 results for the filtered date range.
     */
    public record TopComplaintDTO(
            String complaint,
            Long count
    ) {}

    // Patient Activity DTOs

    /**
     * Visit count grouped by patient category (Student / Faculty / Staff).
     * The category field is a classification label, not a PII identifier.
     */
    public record PatientCategoryCountDTO(
            String category,
            Long count
    ) {}

    /**
     * Daily visit count for a single calendar date.
     * Used for 30-day line charts and calendar heatmaps.
     * Zero-count days are included so charts render a complete series.
     */
    public record DailyVisitVolumeDTO(
            LocalDate date,
            Long visitCount
    ) {}

    // Inventory Analytics DTOs

    /**
     * Inventory item at or below the minimum stock threshold.
     * Item name and stock figures are operational metadata, not patient data.
     */
    public record LowStockItemDTO(
            String itemName,
            Integer stocksOnHand,
            Integer minimumStockLevel
    ) {}

    /**
     * Inventory item expiring within the next 60 days.
     * Returns item name, expiration date, and remaining stock.
     */
    public record ExpiringItemDTO(
            String itemName,
            LocalDate expirationDate,
            Integer stocksOnHand
    ) {}

    // Contacts Analytics DTOs

    /**
     * Count of contact records grouped by mode of communication.
     * The mode label (Phone, SMS, etc.) is a classification, not PII.
     */
    public record ContactModeDistributionDTO(
            String mode,
            Long count
    ) {}

    /**
     * Count of contact records grouped by response outcome.
     * Used for the respond-status breakdown chart.
     */
    public record ContactRespondStatusDTO(
            String status,
            Long count
    ) {}

    /**
     * Show rate for a given month.
     *
     * scheduledCount       — contacts with a linked patient in that month.
     * completedVisitCount  — of those, how many had a matching visit on the same date.
     * showRatePercentage   — completedVisitCount / scheduledCount × 100.
     *
     * Returns 0.0 show rate (not null) when there are no scheduled contacts,
     * so the frontend always receives a valid numeric value.
     */
    public record ShowRateDTO(
            Long scheduledCount,
            Long completedVisitCount,
            Double showRatePercentage
    ) {}

    /**
     * Appointment (contact) count for a single calendar date.
     * Used for 30-day trend charts. Zero-count days are included.
     */
    public record DailyAppointmentsDTO(
            LocalDate date,
            Long appointmentCount
    ) {}
}