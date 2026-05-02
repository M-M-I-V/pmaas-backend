package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.AppointmentDTOs.*;
import dev.mmiv.pmaas.entity.*;
import dev.mmiv.pmaas.repository.AppointmentRepository;
import dev.mmiv.pmaas.repository.PatientsRepository;
import dev.mmiv.pmaas.repository.VisitsRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientsRepository patientsRepository;
    private final VisitsRepository visitsRepository;
    private final AuditLogService auditLogService;

    // ══════════════════════════════════════════════════════════════════════════
    // CREATE
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public AppointmentResponse create(
        AppointmentCreateRequest req,
        Authentication auth
    ) {
        String username = auth.getName();

        Appointment appt = new Appointment();
        appt.setFullName(req.fullName().trim());
        appt.setYearSection(req.yearSection());
        appt.setContactNumber(req.contactNumber());
        appt.setVisitType(req.visitType());
        appt.setChiefComplaint(req.chiefComplaint());
        appt.setAppointmentDate(req.appointmentDate());
        appt.setAppointmentTime(req.appointmentTime());
        appt.setNotes(req.notes());
        appt.setStatus(AppointmentStatus.PENDING);
        appt.setCreatedBy(username);

        if (req.patientId() != null) {
            Patients patient = patientsRepository
                .findById(req.patientId())
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Patient with id " + req.patientId() + " not found."
                    )
                );
            appt.setPatient(patient);
        }

        Appointment saved = appointmentRepository.save(appt);

        auditLogService.record(
            "Appointments",
            Math.toIntExact(saved.getId()),
            "APPOINTMENT_CREATED",
            "Appointment created for " +
                saved.getFullName() +
                " on " +
                saved.getAppointmentDate() +
                " (" +
                saved.getVisitType() +
                ")"
        );

        log.info(
            "Appointment created: id={}, name={}, date={}, type={}",
            saved.getId(),
            saved.getFullName(),
            saved.getAppointmentDate(),
            saved.getVisitType()
        );

        return toResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // READ
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public AppointmentResponse getById(Long id) {
        return toResponse(loadWithDetails(id));
    }

    @Transactional(readOnly = true)
    public Page<AppointmentListItem> getUpcoming(Pageable pageable) {
        Page<Appointment> page = appointmentRepository.findUpcoming(
            LocalDate.now(),
            pageable
        );
        List<AppointmentListItem> items = page
            .getContent()
            .stream()
            .map(this::toListItem)
            .toList();
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<AppointmentListItem> getByDate(LocalDate date) {
        return appointmentRepository
            .findByDateWithPatient(date)
            .stream()
            .map(this::toListItem)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentListItem> getByDateRange(
        LocalDate from,
        LocalDate to
    ) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "fromDate must not be after toDate."
            );
        }
        return appointmentRepository
            .findByDateRangeWithPatient(from, to)
            .stream()
            .map(this::toListItem)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentListItem> getByPatient(Long patientId) {
        return appointmentRepository
            .findByPatientIdOrderByDateDesc(patientId)
            .stream()
            .map(this::toListItem)
            .toList();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public AppointmentResponse update(
        Long id,
        AppointmentUpdateRequest req,
        Authentication auth
    ) {
        Appointment appt = loadWithDetails(id);

        if (appt.getStatus().isTerminal()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot edit an appointment with status " +
                    appt.getStatus().name() +
                    ". Only PENDING and CONFIRMED appointments can be edited."
            );
        }

        if (req.fullName() != null) appt.setFullName(req.fullName().trim());
        if (req.yearSection() != null) appt.setYearSection(req.yearSection());
        if (req.contactNumber() != null) appt.setContactNumber(
            req.contactNumber()
        );
        if (req.visitType() != null) appt.setVisitType(req.visitType());
        if (req.chiefComplaint() != null) appt.setChiefComplaint(
            req.chiefComplaint()
        );
        if (req.appointmentDate() != null) appt.setAppointmentDate(
            req.appointmentDate()
        );
        if (req.appointmentTime() != null) appt.setAppointmentTime(
            req.appointmentTime()
        );
        if (req.notes() != null) appt.setNotes(req.notes());

        if (req.patientId() != null) {
            if (req.patientId() == 0L) {
                // Explicit zero → unlink patient
                appt.setPatient(null);
            } else {
                Patients patient = patientsRepository
                    .findById(req.patientId())
                    .orElseThrow(() ->
                        new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Patient with id " + req.patientId() + " not found."
                        )
                    );
                appt.setPatient(patient);
            }
        }

        appointmentRepository.save(appt);

        auditLogService.record(
            "Appointments",
            Math.toIntExact(id),
            "APPOINTMENT_UPDATED",
            "Appointment updated by " + auth.getName()
        );

        return toResponse(appt);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STATUS CHANGE
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public AppointmentResponse changeStatus(
        Long id,
        AppointmentStatusRequest req,
        Authentication auth
    ) {
        Appointment appt = loadWithDetails(id);
        AppointmentStatus current = appt.getStatus();
        AppointmentStatus next = req.status();

        validateTransition(id, current, next);

        appt.setStatus(next);

        // When marking COMPLETED with a visitId, link the visit record
        if (next == AppointmentStatus.COMPLETED && req.visitId() != null) {
            Visits visit = visitsRepository
                .findById(req.visitId())
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Visit with id " + req.visitId() + " not found."
                    )
                );
            appt.setVisit(visit);
        }

        if (req.notes() != null) appt.setNotes(req.notes());

        appointmentRepository.save(appt);

        auditLogService.record(
            "Appointments",
            Math.toIntExact(id),
            "STATUS_CHANGED",
            "Status changed from " +
                current +
                " to " +
                next +
                " by " +
                auth.getName() +
                (req.visitId() != null
                    ? " (visitId=" + req.visitId() + ")"
                    : "")
        );

        log.info(
            "Appointment {} status: {} → {}, by={}",
            id,
            current,
            next,
            auth.getName()
        );
        return toResponse(appt);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DASHBOARD DATA
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Daily appointment counts for the last N days, zero-filled.
     * Replaces the contacts-based daily appointments dashboard query.
     */
    @Transactional(readOnly = true)
    public List<DailyAppointmentCountDTO> getDailyCountsForLastDays(int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1);

        // Fetch counts broken down by type
        List<Object[]> rows = appointmentRepository.countByDateRangeAndType(
            from,
            to
        );

        // Build a complete day-by-day series with zero-fill
        // Using a simple map approach keyed by date+type
        java.util.Map<LocalDate, long[]> byDate =
            new java.util.LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDate.put(d, new long[] { 0L, 0L }); // [medicalCount, dentalCount]
        }

        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[0]);
            VisitType type = (VisitType) row[1];
            long count = toLong(row[2]);
            if (byDate.containsKey(date)) {
                long[] counts = byDate.get(date);
                if (type == VisitType.MEDICAL) counts[0] = count;
                else counts[1] = count;
            }
        }

        return byDate
            .entrySet()
            .stream()
            .map(e ->
                new DailyAppointmentCountDTO(
                    e.getKey(),
                    e.getValue()[0] + e.getValue()[1],
                    e.getValue()[0],
                    e.getValue()[1]
                )
            )
            .toList();
    }

    /** Count of today's non-cancelled appointments for the KPI card. */
    @Transactional(readOnly = true)
    public long countTodayAppointments() {
        return appointmentRepository.countTodayNonCancelled(LocalDate.now());
    }

    /** Show-rate data by date range. */
    @Transactional(readOnly = true)
    public List<AppointmentShowRateDTO> getShowRateByDateRange(
        LocalDate from,
        LocalDate to
    ) {
        return appointmentRepository
            .showRateByDateRange(from, to)
            .stream()
            .map(row -> {
                long scheduled = toLong(row[1]);
                long completed = toLong(row[2]);
                long noShow = toLong(row[3]);
                double rate =
                    scheduled == 0
                        ? 0.0
                        : Math.round((completed * 10000.0) / scheduled) / 100.0;
                return new AppointmentShowRateDTO(
                    toLocalDate(row[0]),
                    scheduled,
                    completed,
                    noShow,
                    rate
                );
            })
            .toList();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private Appointment loadWithDetails(Long id) {
        return appointmentRepository
            .findByIdWithDetails(id)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Appointment with id " + id + " not found."
                )
            );
    }

    private void validateTransition(
        Long id,
        AppointmentStatus current,
        AppointmentStatus next
    ) {
        if (current.isTerminal()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Appointment " +
                    id +
                    " is already in terminal status " +
                    current.name() +
                    " and cannot be changed."
            );
        }
        boolean valid = switch (current) {
            case PENDING -> next == AppointmentStatus.CONFIRMED ||
            next == AppointmentStatus.CANCELLED;
            case CONFIRMED -> next == AppointmentStatus.COMPLETED ||
            next == AppointmentStatus.NO_SHOW ||
            next == AppointmentStatus.CANCELLED;
            default -> false;
        };
        if (!valid) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid status transition: " + current + " → " + next + "."
            );
        }
    }

    private AppointmentResponse toResponse(Appointment a) {
        return new AppointmentResponse(
            a.getId(),
            a.getPatient() != null ? (long) a.getPatient().getId() : null,
            a.getPatient() != null ? a.getPatient().getName() : null,
            a.getFullName(),
            a.getYearSection(),
            a.getContactNumber(),
            a.getVisitType(),
            a.getChiefComplaint(),
            a.getAppointmentDate(),
            a.getAppointmentTime(),
            a.getStatus(),
            a.getVisit() != null ? a.getVisit().getId() : null,
            a.getNotes(),
            a.getCreatedBy(),
            a.getCreatedAt(),
            a.getUpdatedAt()
        );
    }

    private AppointmentListItem toListItem(Appointment a) {
        return new AppointmentListItem(
            a.getId(),
            a.getFullName(),
            a.getYearSection(),
            a.getContactNumber(),
            a.getVisitType(),
            a.getChiefComplaint(),
            a.getAppointmentDate(),
            a.getAppointmentTime(),
            a.getStatus(),
            a.getPatient() != null ? (long) a.getPatient().getId() : null,
            a.getVisit() != null ? a.getVisit().getId() : null
        );
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.sql.Timestamp ts) return ts
            .toLocalDateTime()
            .toLocalDate();
        return null;
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        return ((Number) value).longValue();
    }
}
