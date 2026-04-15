package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.DashboardDTOs.*;
import dev.mmiv.pmaas.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KPI Controller — lightweight single-metric endpoints for dashboard header cards.
 *
 * Base path: /api/dashboard/kpis
 *
 * Authorization: clinical roles only (MD, DMD, NURSE).
 * ADMIN is excluded from clinical analytics by design — administrative accounts
 * should use separate reporting tools and not have visibility into clinical KPIs
 * unless the clinic principal explicitly grants it.
 *
 * All responses are aggregated counts — no PII is returned.
 */
@RestController
@RequestMapping("/api/dashboard/kpis")
@RequiredArgsConstructor
public class KpiController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/kpis/total-visits
     *
     * Returns total visits for today and month-to-date in a single response.
     * Backend computes both metrics in one SQL round-trip.
     *
     * Example response:
     * {
     *   "todayCount": 12,
     *   "monthToDateCount": 94
     * }
     */
    @GetMapping("/total-visits")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public TotalVisitsKpiDTO getTotalVisits() {
        return dashboardService.getTotalVisitsKpi();
    }

    /**
     * GET /api/dashboard/kpis/visit-ratio
     *
     * Medical vs Dental visit split for the current month.
     *
     * Example response:
     * {
     *   "medicalCount": 61,
     *   "dentalCount": 33
     * }
     */
    @GetMapping("/visit-ratio")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public VisitRatioDTO getVisitRatio() {
        return dashboardService.getVisitRatioKpi();
    }

    /**
     * GET /api/dashboard/kpis/high-risk-patients
     *
     * Count of patients with at least one flagged condition.
     * Returns a count only — no patient identifiers or condition details.
     *
     * Example response:
     * {
     *   "count": 7
     * }
     */
    @GetMapping("/high-risk-patients")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public HighRiskPatientsKpiDTO getHighRiskPatients() {
        return dashboardService.getHighRiskPatientsKpi();
    }

    /**
     * GET /api/dashboard/kpis/critical-inventory
     *
     * Count of inventory items with zero stock.
     * Drives the stock alert badge in the dashboard header.
     *
     * Example response:
     * {
     *   "count": 3
     * }
     */
    @GetMapping("/critical-inventory")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public CriticalInventoryKpiDTO getCriticalInventory() {
        return dashboardService.getCriticalInventoryKpi();
    }

    /**
     * GET /api/dashboard/kpis/appointments-today
     *
     * Count of contact records logged for today.
     * Contacts represent scheduling and communication events.
     *
     * Example response:
     * {
     *   "count": 8
     * }
     */
    @GetMapping("/appointments-today")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public AppointmentsTodayKpiDTO getAppointmentsToday() {
        return dashboardService.getAppointmentsTodayKpi();
    }
}