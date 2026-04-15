package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.DashboardDTOs.*;
import dev.mmiv.pmaas.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Patient Activity Controller — visit volume trends and demographic distribution.
 *
 * Base path: /api/dashboard/activity
 *
 * All responses contain aggregated counts only. Patient category
 * (Student / Faculty / Staff) is a classification label, not a PII identifier.
 * No patient names, IDs, or contact details are returned.
 */
@RestController
@RequestMapping("/api/dashboard/activity")
@RequiredArgsConstructor
public class PatientActivityController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/activity/demographics
     *
     * Visit counts grouped by patient category for the given date range.
     * Defaults to current month when dates are omitted.
     *
     * Query parameters:
     *   startDate  (ISO date, optional)
     *   endDate    (ISO date, optional)
     *
     * Example response:
     * [
     *   { "category": "Student",  "count": 87 },
     *   { "category": "Faculty",  "count": 23 },
     *   { "category": "Staff",    "count": 14 }
     * ]
     */
    @GetMapping("/demographics")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public List<PatientCategoryCountDTO> getDemographics(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return dashboardService.getDemographicsBreakdown(startDate, endDate);
    }

    /**
     * GET /api/dashboard/activity/daily-volume
     *
     * Daily visit counts for the last 30 days.
     * All 30 days are returned — days with zero visits have visitCount = 0.
     * Used for line charts and calendar heatmaps.
     *
     * No query parameters. The 30-day window is always anchored to today.
     *
     * Example response:
     * [
     *   { "date": "2025-03-17", "visitCount": 0  },
     *   { "date": "2025-03-18", "visitCount": 7  },
     *   { "date": "2025-03-19", "visitCount": 14 }
     * ]
     */
    @GetMapping("/daily-volume")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public List<DailyVisitVolumeDTO> getDailyVolume() {
        return dashboardService.getDailyVisitVolume();
    }
}