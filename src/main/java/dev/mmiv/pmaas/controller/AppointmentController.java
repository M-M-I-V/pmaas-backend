package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.AppointmentDTOs.*;
import dev.mmiv.pmaas.service.AppointmentService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Appointments REST controller.
 *
 * BASE PATH: /api/appointments
 *
 * PERMISSION SUMMARY:
 *   All clinical roles (NURSE, MD, DMD) can read and create appointments.
 *   Status changes (confirm, complete, cancel, no-show) are restricted to
 *   NURSE and MD/DMD because:
 *     - NURSE confirms and cancels on behalf of the clinic
 *     - MD/DMD marks COMPLETED when they link a visit after the encounter
 *   ADMIN is excluded from all appointment data by design.
 *
 * NO DELETE ENDPOINT:
 *   Appointments are never deleted. A cancelled appointment is historical
 *   evidence that a slot was requested. Use POST /{id}/status with
 *   status=CANCELLED to cancel an appointment.
 *
 * DASHBOARD ENDPOINTS (no auth role restriction beyond clinical staff):
 *   GET /api/appointments/dashboard/daily   — daily counts for chart
 *   GET /api/appointments/dashboard/today   — KPI count for today
 *   GET /api/appointments/dashboard/show-rate — show-rate by date range
 */
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * POST /api/appointments
     *
     * Creates a new appointment in PENDING status. Any clinical role may create.
     * patientId is optional — link after the patient registers if needed.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public AppointmentResponse create(
        @Valid @RequestBody AppointmentCreateRequest request,
        Authentication auth
    ) {
        return appointmentService.create(request, auth);
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    /**
     * GET /api/appointments/{id}
     *
     * Full appointment detail including patient name and linked visit ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public AppointmentResponse getById(@PathVariable Long id) {
        return appointmentService.getById(id);
    }

    /**
     * GET /api/appointments/upcoming?page=0&size=20
     *
     * Paginated list of upcoming (PENDING + CONFIRMED) appointments from today.
     * Used by the nurse queue view on the appointments page.
     */
    @GetMapping("/upcoming")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public Page<AppointmentListItem> getUpcoming(
        @PageableDefault(
            size = 20,
            sort = "appointmentDate",
            direction = Sort.Direction.ASC
        ) Pageable pageable
    ) {
        return appointmentService.getUpcoming(pageable);
    }

    /**
     * GET /api/appointments/by-date?date=2026-05-01
     *
     * All appointments for a specific date, ordered by time.
     * Used by the daily calendar view.
     */
    @GetMapping("/by-date")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public List<AppointmentListItem> getByDate(
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate date
    ) {
        return appointmentService.getByDate(date);
    }

    /**
     * GET /api/appointments/by-range?from=2026-05-01&to=2026-05-31
     *
     * All appointments within a date range.
     * Used by the calendar week/month view and export.
     */
    @GetMapping("/by-range")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public List<AppointmentListItem> getByDateRange(
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate from,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate to
    ) {
        return appointmentService.getByDateRange(from, to);
    }

    /**
     * GET /api/appointments/patient/{patientId}
     *
     * All appointments for a specific patient, newest first.
     * Used by the patient profile page.
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public List<AppointmentListItem> getByPatient(
        @PathVariable Long patientId
    ) {
        return appointmentService.getByPatient(patientId);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * PUT /api/appointments/{id}
     *
     * Edit appointment details (date, time, contact number, etc.).
     * Rejected for terminal-status appointments (COMPLETED, CANCELLED, NO_SHOW).
     * All fields are optional — null means no change.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public AppointmentResponse update(
        @PathVariable Long id,
        @Valid @RequestBody AppointmentUpdateRequest request,
        Authentication auth
    ) {
        return appointmentService.update(id, request, auth);
    }

    /**
     * POST /api/appointments/{id}/status
     *
     * Advance or terminate the appointment lifecycle.
     *
     * Valid transitions:
     *   PENDING   → CONFIRMED  (nurse confirms the slot)
     *   PENDING   → CANCELLED
     *   CONFIRMED → COMPLETED  (patient showed up — optionally supply visitId)
     *   CONFIRMED → NO_SHOW    (patient did not appear)
     *   CONFIRMED → CANCELLED
     *
     * When transitioning to COMPLETED with a visitId, the appointment is linked
     * to the clinical visit record, enabling accurate show-rate computation.
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public AppointmentResponse changeStatus(
        @PathVariable Long id,
        @Valid @RequestBody AppointmentStatusRequest request,
        Authentication auth
    ) {
        return appointmentService.changeStatus(id, request, auth);
    }

    // ── DASHBOARD ─────────────────────────────────────────────────────────────

    /**
     * GET /api/appointments/dashboard/daily?days=30
     *
     * Daily appointment counts for the last N days (default 30), zero-filled.
     * Broken down by MEDICAL and DENTAL.
     * Replaces the contacts-based daily appointments chart in the dashboard.
     *
     * Example response:
     * [
     *   { "date": "2026-04-01", "totalCount": 5, "medicalCount": 3, "dentalCount": 2 },
     *   { "date": "2026-04-02", "totalCount": 0, "medicalCount": 0, "dentalCount": 0 }
     * ]
     */
    @GetMapping("/dashboard/daily")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public List<DailyAppointmentCountDTO> getDailyDashboard(
        @RequestParam(defaultValue = "30") int days
    ) {
        if (days < 1 || days > 365) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "days must be between 1 and 365."
            );
        }
        return appointmentService.getDailyCountsForLastDays(days);
    }

    /**
     * GET /api/appointments/dashboard/today
     *
     * Count of today's non-cancelled appointments.
     * Used by the dashboard KPI card. Returns a plain number, not a DTO,
     * so the frontend can use it directly without unwrapping.
     *
     * Example response: 8
     */
    @GetMapping("/dashboard/today")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public long getTodayCount() {
        return appointmentService.countTodayAppointments();
    }

    /**
     * GET /api/appointments/dashboard/show-rate?from=2026-04-01&to=2026-04-30
     *
     * Show-rate by date within the given range.
     * show rate = COMPLETED appointments / total non-cancelled appointments.
     *
     * Example response:
     * [
     *   {
     *     "date": "2026-04-01",
     *     "scheduledCount": 8,
     *     "completedCount": 6,
     *     "noShowCount": 1,
     *     "showRatePercentage": 75.0
     *   }
     * ]
     */
    @GetMapping("/dashboard/show-rate")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public List<AppointmentShowRateDTO> getShowRate(
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate from,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate to
    ) {
        return appointmentService.getShowRateByDateRange(from, to);
    }
}
