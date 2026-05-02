package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.DashboardDTOs.*;
import dev.mmiv.pmaas.service.AppointmentService;
import dev.mmiv.pmaas.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KPI Controller — lightweight single-metric endpoints for dashboard header cards.
 *
 * V14 CHANGE: appointments-today now delegates to AppointmentService instead of
 * DashboardService. The contacts table was the previous source for this KPI,
 * which was semantically wrong — contacts are communication logs, not appointments.
 * The new appointments table is the correct authoritative source.
 */
@RestController
@RequestMapping("/api/dashboard/kpis")
@RequiredArgsConstructor
public class KpiController {

    private final DashboardService dashboardService;
    private final AppointmentService appointmentService;

    @GetMapping("/total-visits")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public TotalVisitsKpiDTO getTotalVisits() {
        return dashboardService.getTotalVisitsKpi();
    }

    @GetMapping("/visit-ratio")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public VisitRatioDTO getVisitRatio() {
        return dashboardService.getVisitRatioKpi();
    }

    @GetMapping("/high-risk-patients")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public HighRiskPatientsKpiDTO getHighRiskPatients() {
        return dashboardService.getHighRiskPatientsKpi();
    }

    @GetMapping("/critical-inventory")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public CriticalInventoryKpiDTO getCriticalInventory() {
        return dashboardService.getCriticalInventoryKpi();
    }

    /**
     * GET /api/dashboard/kpis/appointments-today
     *
     * V14: Now sourced from the appointments table (non-cancelled appointments
     * for today) via AppointmentService.countTodayAppointments().
     * Previously used contacts.contact_date = CURRENT_DATE which was wrong.
     */
    @GetMapping("/appointments-today")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public AppointmentsTodayKpiDTO getAppointmentsToday() {
        return new AppointmentsTodayKpiDTO(
            appointmentService.countTodayAppointments()
        );
    }
}
