package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.*;
import dev.mmiv.pmaas.entity.*;
import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.entity.UserPrincipal;
import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.entity.VisitType;
import dev.mmiv.pmaas.exception.*;
import dev.mmiv.pmaas.repository.*;
import dev.mmiv.pmaas.repository.PatientsRepository;
import dev.mmiv.pmaas.repository.UsersRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates all multi-step visit workflow operations.
 *
 * V13 CHANGES:
 *   - completeDmdSection: field mapping updated to match V13 base entity layout.
 *     history replaces dentalNotes; treatment replaces treatmentProvided;
 *     toothStatus replaces toothInvolved. All base clinical fields are set
 *     via the inherited Visits setters.
 *   - toMedicalResponse / toDentalResponse: updated to include all fields
 *     present in the revised response DTOs (medicalChartImage,
 *     toothStatus, dentalChartImage, physicalExamFindings for dental, etc.).
 *   - All other workflow steps are unchanged.
 *
 * RESPONSIBILITIES:
 *   1. Validate state transitions via VisitStatus.validateTransition()
 *   2. Enforce ownership rules (assigned user check)
 *   3. Perform all mutations inside @Transactional methods
 *   4. Emit audit log entries for every workflow event
 *   5. Map entities to DTOs — never return raw entities
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitWorkflowService {

    private final VisitsRepository visitsRepository;
    private final NurseNoteRepository nurseNoteRepository;
    private final PatientsRepository patientsRepository;
    private final UsersRepository usersRepository;
    private final AuditLogService auditLogService;

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 1 — NURSE CREATES INITIAL VISIT
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public VisitSummaryResponse createMedicalVisit(
        MedicalVisitCreateRequest req,
        Authentication auth
    ) {
        String username = username(auth);
        Patients patient = loadPatient(req.patientId());

        MedicalVisits visit = new MedicalVisits();
        visit.setPatient(patient);
        visit.setVisitDate(req.visitDate());
        visit.setStatus(VisitStatus.CREATED_BY_NURSE);
        visit.setCreatedBy(username);
        visit.setChiefComplaint(req.chiefComplaint());
        visit.setTemperature(req.temperature());
        visit.setBloodPressure(req.bloodPressure());
        visit.setPulseRate(req.pulseRate());
        visit.setRespiratoryRate(req.respiratoryRate());
        visit.setSpo2(req.spo2());

        MedicalVisits saved = visitsRepository.save(visit);

        auditLogService.record(
            "MedicalVisits",
            Math.toIntExact(saved.getId()),
            "VISIT_CREATED",
            "Medical visit created for patient ID " +
                patient.getId() +
                ", chief complaint: " +
                truncate(req.chiefComplaint(), 100)
        );

        log.info(
            "Medical visit created: id={}, patient={}, by={}",
            saved.getId(),
            patient.getId(),
            username
        );
        return toSummary(saved);
    }

    @Transactional
    public VisitSummaryResponse createDentalVisit(
        DentalVisitCreateRequest req,
        Authentication auth
    ) {
        String username = username(auth);
        Patients patient = loadPatient(req.patientId());

        DentalVisits visit = new DentalVisits();
        visit.setPatient(patient);
        visit.setVisitDate(req.visitDate());
        visit.setStatus(VisitStatus.CREATED_BY_NURSE);
        visit.setCreatedBy(username);
        visit.setChiefComplaint(req.chiefComplaint());
        visit.setTemperature(req.temperature());
        visit.setBloodPressure(req.bloodPressure());
        visit.setPulseRate(req.pulseRate());

        DentalVisits saved = visitsRepository.save(visit);

        auditLogService.record(
            "DentalVisits",
            Math.toIntExact(saved.getId()),
            "VISIT_CREATED",
            "Dental visit created for patient ID " +
                patient.getId() +
                ", chief complaint: " +
                truncate(req.chiefComplaint(), 100)
        );

        log.info(
            "Dental visit created: id={}, patient={}, by={}",
            saved.getId(),
            patient.getId(),
            username
        );
        return toSummary(saved);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 2 — NURSE ASSIGNS TO MD OR DMD
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public VisitSummaryResponse assignVisit(
        Long visitId,
        VisitAssignRequest req,
        Authentication auth
    ) {
        String username = username(auth);
        Visits visit = loadVisit(visitId);

        VisitType type = visit.getVisitType();
        VisitStatus target = switch (type) {
            case MEDICAL -> VisitStatus.PENDING_MD_REVIEW;
            case DENTAL -> VisitStatus.PENDING_DMD_REVIEW;
        };

        visit.getStatus().validateTransition(target, type, visitId);

        Users assignee = usersRepository
            .findById(Math.toIntExact(req.assignToUserId()))
            .orElseThrow(() -> new UserNotFoundException(req.assignToUserId()));

        validateAssigneeRole(assignee, type, visitId);

        visit.setStatus(target);
        visit.setAssignedToUserId((long) assignee.getId());
        visit.setAssignedBy(username);
        visit.setAssignedAt(LocalDateTime.now());

        visitsRepository.save(visit);

        auditLogService.record(
            entityName(visit),
            Math.toIntExact(visitId),
            "VISIT_ASSIGNED",
            "Visit assigned to " +
                assignee.getRole() +
                " (user ID " +
                assignee.getId() +
                ")" +
                (req.comments() != null
                    ? ", reason: " + truncate(req.comments(), 200)
                    : "")
        );

        log.info(
            "Visit assigned: id={}, to={}, by={}",
            visitId,
            assignee.getId(),
            username
        );
        return toSummary(visit);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 3a — MD COMPLETES MEDICAL SECTION
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalVisitResponse completeMdSection(
        Long visitId,
        MedicalVisitCompletionRequest req,
        Authentication auth
    ) {
        String username = username(auth);
        Visits base = loadVisit(visitId);

        validateAssignment(base, visitId, userId(auth));

        if (!(base instanceof MedicalVisits visit)) {
            throw new InvalidStateTransitionException(
                visitId,
                base.getStatus(),
                VisitStatus.PENDING_NURSE_REVIEW,
                "Visit " + visitId + " is not a medical visit."
            );
        }

        visit
            .getStatus()
            .validateTransition(
                VisitStatus.PENDING_NURSE_REVIEW,
                VisitType.MEDICAL,
                visitId
            );

        // All clinical fields now live on the base Visits entity (after V13)
        visit.setHistory(req.history());
        visit.setPhysicalExamFindings(req.physicalExamFindings());
        visit.setDiagnosis(req.diagnosis());
        visit.setPlan(req.plan());
        visit.setTreatment(req.treatment());
        visit.setDiagnosticTestResult(req.diagnosticTestResult());
        visit.setHama(req.hama());
        visit.setReferralForm(req.referralForm());
        visit.setDiagnosticTestImage(req.diagnosticTestImage());
        // MedicalVisits-specific
        visit.setMedicalChartImage(req.medicalChartImage());

        visit.setStatus(VisitStatus.PENDING_NURSE_REVIEW);

        visitsRepository.save(visit);

        auditLogService.record(
            "MedicalVisits",
            Math.toIntExact(visitId),
            "MD_SECTION_COMPLETED",
            "MD completed medical section, diagnosis: " +
                truncate(req.diagnosis(), 100)
        );

        log.info("MD section completed: visitId={}, by={}", visitId, username);
        return toMedicalResponse(visit);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 3b — DMD COMPLETES DENTAL SECTION
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public DentalVisitResponse completeDmdSection(
        Long visitId,
        DentalVisitCompletionRequest req,
        Authentication auth
    ) {
        String username = username(auth);
        Visits base = loadVisit(visitId);

        validateAssignment(base, visitId, userId(auth));

        if (!(base instanceof DentalVisits visit)) {
            throw new InvalidStateTransitionException(
                visitId,
                base.getStatus(),
                VisitStatus.COMPLETED,
                "Visit " + visitId + " is not a dental visit."
            );
        }

        visit
            .getStatus()
            .validateTransition(
                VisitStatus.COMPLETED,
                VisitType.DENTAL,
                visitId
            );

        // Base entity clinical fields (visits table after V13)
        visit.setDiagnosis(req.diagnosis());
        visit.setHistory(req.history()); // was dentalNotes
        visit.setPhysicalExamFindings(req.physicalExamFindings());
        visit.setTreatment(req.treatment()); // was treatmentProvided
        visit.setPlan(req.plan());
        visit.setReferralForm(req.referralForm());

        // DentalVisits-specific fields
        visit.setToothStatus(req.toothStatus()); // was toothInvolved
        visit.setDentalChartImage(req.dentalChartImage());

        visit.setStatus(VisitStatus.COMPLETED);
        visit.setCompletedAt(LocalDateTime.now());

        visitsRepository.save(visit);

        auditLogService.record(
            "DentalVisits",
            Math.toIntExact(visitId),
            "DMD_SECTION_COMPLETED",
            "DMD completed dental section, diagnosis: " +
                truncate(req.diagnosis(), 100)
        );

        log.info("DMD section completed: visitId={}, by={}", visitId, username);
        return toDentalResponse(visit);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 4 — NURSE ADDS TIMESTAMPED NOTE (medical only)
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MedicalVisitResponse addNurseNote(
        Long visitId,
        NurseNoteRequest req,
        Authentication auth
    ) {
        String username = username(auth);
        Visits base = loadVisit(visitId);

        if (!(base instanceof MedicalVisits visit)) {
            throw new InvalidStateTransitionException(
                visitId,
                base.getStatus(),
                base.getStatus(),
                "Nurse notes can only be added to medical visits."
            );
        }

        if (!visit.getStatus().isNoteAddAllowed()) {
            throw new InvalidStateTransitionException(
                visitId,
                visit.getStatus(),
                visit.getStatus(),
                "Cannot add notes to a visit in " +
                    visit.getStatus().name() +
                    " status."
            );
        }

        NurseNote note = new NurseNote(visit, req.content(), username);
        nurseNoteRepository.save(note);
        visit.addNurseNote(note);

        // First note in PENDING_NURSE_REVIEW → COMPLETED
        if (visit.getStatus() == VisitStatus.PENDING_NURSE_REVIEW) {
            visit.setStatus(VisitStatus.COMPLETED);
            visit.setCompletedAt(LocalDateTime.now());
            visitsRepository.save(visit);
        }

        auditLogService.record(
            "MedicalVisits",
            Math.toIntExact(visitId),
            "NURSE_NOTE_ADDED",
            "Nurse note added, visit status: " + visit.getStatus().name()
        );

        log.info(
            "Nurse note added: visitId={}, by={}, newStatus={}",
            visitId,
            username,
            visit.getStatus()
        );
        return toMedicalResponse(visit);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private Visits loadVisit(Long visitId) {
        return visitsRepository
            .findByIdWithPatient(visitId)
            .orElseThrow(() -> new PatientNotFoundException(visitId));
    }

    private Patients loadPatient(Long patientId) {
        return patientsRepository
            .findById(patientId)
            .orElseThrow(() -> new PatientNotFoundException(patientId));
    }

    private void validateAssignment(
        Visits visit,
        Long visitId,
        Long requestingUserId
    ) {
        Long assigned = visit.getAssignedToUserId();
        if (assigned == null || !assigned.equals(requestingUserId)) {
            throw new UnauthorizedVisitAccessException(
                visitId,
                assigned,
                requestingUserId,
                "Only the assigned clinic staff can modify this visit."
            );
        }
    }

    private void validateAssigneeRole(
        Users assignee,
        VisitType type,
        Long visitId
    ) {
        Role role = assignee.getRole();
        boolean valid = switch (type) {
            case MEDICAL -> role == Role.MD;
            case DENTAL -> role == Role.DMD;
        };
        if (!valid) {
            throw new InvalidStateTransitionException(
                visitId,
                VisitStatus.CREATED_BY_NURSE,
                type == VisitType.MEDICAL
                    ? VisitStatus.PENDING_MD_REVIEW
                    : VisitStatus.PENDING_DMD_REVIEW,
                "Cannot assign " +
                    type.name() +
                    " visit to user with role: " +
                    role
            );
        }
    }

    private String username(Authentication auth) {
        return auth.getName();
    }

    private Long userId(Authentication auth) {
        if (
            auth.getPrincipal() instanceof UserPrincipal up
        ) return (long) up.getId();
        throw new IllegalStateException(
            "Cannot extract user ID from authentication."
        );
    }

    private String entityName(Visits visit) {
        return visit instanceof MedicalVisits
            ? "MedicalVisits"
            : "DentalVisits";
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    // ── DTO mappers ───────────────────────────────────────────────────────────

    private VisitSummaryResponse toSummary(Visits v) {
        return new VisitSummaryResponse(
            v.getId(),
            v.getStatus(),
            v.getVisitType(),
            (long) v.getPatient().getId(),
            v.getPatient().getName(),
            v.getAssignedToUserId(),
            v.getAssignedBy(),
            v.getAssignedAt(),
            v.getCreatedBy(),
            v.getCreatedAt()
        );
    }

    public MedicalVisitResponse toMedicalResponse(MedicalVisits v) {
        List<NurseNoteResponse> notes = v
            .getNurseNotes()
            .stream()
            .map(NurseNoteResponse::from)
            .toList();
        return new MedicalVisitResponse(
            v.getId(),
            v.getStatus(),
            (long) v.getPatient().getId(),
            v.getPatient().getName(),
            v.getVisitDate(),
            // Nurse vitals (base)
            v.getChiefComplaint(),
            v.getTemperature(),
            v.getBloodPressure(),
            v.getPulseRate(),
            v.getRespiratoryRate(),
            v.getSpo2(),
            // MD clinical section (base entity after V13)
            v.getHistory(),
            v.getPhysicalExamFindings(),
            v.getDiagnosis(),
            v.getPlan(),
            v.getTreatment(),
            v.getDiagnosticTestResult(),
            v.getHama(),
            v.getReferralForm(),
            v.getDiagnosticTestImage(),
            // Medical-specific
            v.getMedicalChartImage(),
            notes,
            // Audit
            v.getCreatedBy(),
            v.getCreatedAt(),
            v.getAssignedToUserId(),
            v.getAssignedAt(),
            v.getCompletedAt()
        );
    }

    public DentalVisitResponse toDentalResponse(DentalVisits v) {
        return new DentalVisitResponse(
            v.getId(),
            v.getStatus(),
            (long) v.getPatient().getId(),
            v.getPatient().getName(),
            v.getVisitDate(),
            // Nurse vitals (base)
            v.getChiefComplaint(),
            v.getTemperature(),
            v.getBloodPressure(),
            v.getPulseRate(),
            // DMD clinical section (base entity after V13)
            v.getHistory(),
            v.getPhysicalExamFindings(),
            v.getDiagnosis(),
            v.getPlan(),
            v.getTreatment(),
            v.getReferralForm(),
            // Dental-specific
            v.getToothStatus(),
            v.getDentalChartImage(),
            // Audit
            v.getCreatedBy(),
            v.getCreatedAt(),
            v.getAssignedToUserId(),
            v.getAssignedAt(),
            v.getCompletedAt()
        );
    }
}
