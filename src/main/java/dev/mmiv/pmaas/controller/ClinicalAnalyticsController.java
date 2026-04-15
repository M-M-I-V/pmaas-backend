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
 * Clinical Analytics Controller — trends and clinical pattern analysis.
 *
 * Base path: /api/dashboard/clinical
 *
 * Authorization: clinical roles only.
 * These endpoints expose diagnosis and complaint text — while this is not
 * directly PII, clinical pattern data is protected under RA 10173 as health
 * information. Access is restricted to licensed clinical staff.
 *
 * All date parameters default to the current calendar month when omitted.
 */
@RestController
@RequestMapping("/api/dashboard/clinical")
@RequiredArgsConstructor
public class ClinicalAnalyticsController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/clinical/top-diagnoses
     *
     * Top 10 most frequent diagnosis values in the given date range.
     * Defaults to current month when startDate/endDate are omitted.
     *
     * Query parameters:
     *   startDate  (ISO date, optional) — e.g. 2025-01-01
     *   endDate    (ISO date, optional) — e.g. 2025-06-30
     *
     * Example response:
     * [
     *   { "diagnosis": "Upper Respiratory Tract Infection", "count": 34 },
     *   { "diagnosis": "Hypertension",                     "count": 21 }
     * ]
     */
    @GetMapping("/top-diagnoses")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public List<TopDiagnosisDTO> getTopDiagnoses(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return dashboardService.getTopDiagnoses(startDate, endDate);
    }

    /**
     * GET /api/dashboard/clinical/top-complaints
     *
     * Top 10 most frequent chief complaint values in the given date range.
     *
     * Query parameters:
     *   startDate  (ISO date, optional)
     *   endDate    (ISO date, optional)
     *
     * Example response:
     * [
     *   { "complaint": "Headache",     "count": 28 },
     *   { "complaint": "Stomachache",  "count": 19 }
     * ]
     */
    @GetMapping("/top-complaints")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public List<TopComplaintDTO> getTopComplaints(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return dashboardService.getTopComplaints(startDate, endDate);
    }
}