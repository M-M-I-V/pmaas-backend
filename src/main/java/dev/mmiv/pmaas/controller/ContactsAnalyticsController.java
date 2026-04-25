package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.DashboardDTOs.*;
import dev.mmiv.pmaas.service.DashboardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contacts Analytics Controller — communication, scheduling, and show-rate metrics.
 *
 * Base path: /api/dashboard/contacts
 *
 * IMPORTANT DOMAIN DISTINCTION:
 *   Contact = communication/scheduling event (phone call, SMS, walk-in coordination)
 *   Visit   = completed clinical encounter
 *
 * The show rate metric joins these two concepts: what percentage of scheduled
 * contacts (appointments) resulted in an actual clinical visit.
 *
 * All responses contain aggregated metrics only.
 * No contact names, phone numbers, or message content is returned.
 */
@RestController
@RequestMapping("/api/dashboard/contacts")
@RequiredArgsConstructor
public class ContactsAnalyticsController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/contacts/mode-distribution
     *
     * Count of contact records grouped by communication mode.
     * Used for pie/bar charts showing how the clinic communicates with patients.
     *
     * Example response:
     * [
     *   { "mode": "PHONE",     "count": 45 },
     *   { "mode": "SMS",       "count": 32 },
     *   { "mode": "WALK_IN",   "count": 18 },
     *   { "mode": "IN_PERSON", "count": 12 },
     *   { "mode": "EMAIL",     "count":  7 }
     * ]
     */
    @GetMapping("/mode-distribution")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public List<ContactModeDistributionDTO> getModeDistribution() {
        return dashboardService.getContactModeDistribution();
    }

    /**
     * GET /api/dashboard/contacts/respond-status
     *
     * Count of contact records grouped by response outcome.
     * Used for the respond-status breakdown showing follow-up workload.
     *
     * Example response:
     * [
     *   { "status": "RESPONDED",           "count": 58 },
     *   { "status": "PENDING",             "count": 17 },
     *   { "status": "NO_RESPONSE",         "count": 12 },
     *   { "status": "LEFT_MESSAGE",        "count":  8 },
     *   { "status": "CALLBACK_REQUESTED",  "count":  5 }
     * ]
     */
    @GetMapping("/respond-status")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public List<ContactRespondStatusDTO> getRespondStatus() {
        return dashboardService.getContactRespondStatus();
    }

    /**
     * GET /api/dashboard/contacts/show-rate
     *
     * Percentage of scheduled contacts (contacts with a linked patient) that
     * resulted in an actual visit on the same date.
     *
     * Defaults to the current month when year/month are omitted.
     *
     * Query parameters:
     *   year   (integer, optional) — e.g. 2025
     *   month  (integer 1–12, optional) — e.g. 4
     *
     * Example response:
     * {
     *   "scheduledCount":      42,
     *   "completedVisitCount": 31,
     *   "showRatePercentage":  73.81
     * }
     *
     * A 0.0 showRatePercentage is returned when scheduledCount = 0,
     * so the frontend always receives a valid numeric value.
     */
    @GetMapping("/show-rate-daily")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public List<ShowRateDailyDTO> getShowRateDaily(
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Integer month
    ) {
        return dashboardService.getShowRateDaily(year, month);
    }

    /**
     * GET /api/dashboard/contacts/daily-appointments
     *
     * Number of contact records (scheduled appointments) per day for the
     * last 30 days. Zero-count days are included for complete chart rendering.
     *
     * No query parameters — anchored to today.
     *
     * Example response:
     * [
     *   { "date": "2025-03-17", "appointmentCount": 0  },
     *   { "date": "2025-03-18", "appointmentCount": 5  },
     *   { "date": "2025-03-19", "appointmentCount": 11 }
     * ]
     */
    @GetMapping("/daily-appointments")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public List<DailyAppointmentsDTO> getDailyAppointments() {
        return dashboardService.getDailyAppointments();
    }
}
