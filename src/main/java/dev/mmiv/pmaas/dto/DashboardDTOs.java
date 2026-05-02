package dev.mmiv.pmaas.dto;

import java.time.LocalDate;

/**
 * All Analytics Dashboard DTOs in one file.
 *
 * V14 CHANGE: AppointmentsTodayKpiDTO now represents the count from the
 * new appointments table, not the contacts table. The KpiController and
 * DashboardService.getAppointmentsTodayKpi() are updated to call
 * AppointmentService.countTodayAppointments() instead of
 * DashboardRepository.fetchAppointmentsTodayCount().
 *
 * The contacts-based daily appointments chart (DailyAppointmentsDTO) is
 * retained for backward compatibility but the dashboard page should
 * prefer the new AppointmentDTOs.DailyAppointmentCountDTO from
 * GET /api/appointments/dashboard/daily.
 */
public final class DashboardDTOs {

    private DashboardDTOs() {}

    // KPI DTOs

    public record TotalVisitsKpiDTO(Long todayCount, Long monthToDateCount) {}

    public record VisitRatioDTO(Long medicalCount, Long dentalCount) {}

    public record HighRiskPatientsKpiDTO(Long count) {}

    public record CriticalInventoryKpiDTO(Long count) {}

    /**
     * Count of today's non-cancelled appointments.
     * V14: sourced from the appointments table via AppointmentService,
     * not from the contacts table.
     */
    public record AppointmentsTodayKpiDTO(Long count) {}

    // Clinical Analytics DTOs
    public record TopDiagnosisDTO(String diagnosis, Long count) {}

    public record TopComplaintDTO(String complaint, Long count) {}

    // Patient Activity DTOs
    public record PatientCategoryCountDTO(String category, Long count) {}

    public record DailyVisitVolumeDTO(LocalDate date, Long visitCount) {}

    // Inventory Analytics DTOs
    public record LowStockItemDTO(
        String itemName,
        Integer stocksOnHand,
        Integer minimumStockLevel
    ) {}

    public record ExpiringItemDTO(
        String itemName,
        LocalDate expirationDate,
        Integer stocksOnHand
    ) {}

    // Contacts Analytics DTOs (retained — contacts chart still uses these)
    public record ContactModeDistributionDTO(String mode, Long count) {}

    public record ContactRespondStatusDTO(String status, Long count) {}

    public record ShowRateDailyDTO(
        LocalDate date,
        Long scheduledCount,
        Long completedVisitCount,
        Double showRatePercentage
    ) {}

    /**
     * Daily appointments from the contacts table — retained for the
     * contacts analytics section. The appointments dashboard chart
     * uses AppointmentDTOs.DailyAppointmentCountDTO instead.
     */
    public record DailyAppointmentsDTO(LocalDate date, Long appointmentCount) {}
}
