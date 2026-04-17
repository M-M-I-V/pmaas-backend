package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.VisitEditDTOs.DentalVisitEditRequest;
import dev.mmiv.pmaas.dto.VisitEditDTOs.MedicalVisitEditRequest;
import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.DentalVisitResponse;
import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.MedicalVisitResponse;
import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.NurseNoteResponse;
import dev.mmiv.pmaas.entity.*;
import dev.mmiv.pmaas.entity.UserPrincipal;
import dev.mmiv.pmaas.exception.InvalidStateTransitionException;
import dev.mmiv.pmaas.exception.UnauthorizedVisitAccessException;
import dev.mmiv.pmaas.repository.VisitManagementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles in-place editing of visits within the workflow.
 *
 * EDIT RULES — enforced at service level (not just @PreAuthorize, which
 * checks role only; service checks role AND status AND assignment):
 *
 *   Medical:
 *     NURSE  + CREATED_BY_NURSE    → edit nurse vitals section
 *     MD     + PENDING_MD_REVIEW   → edit MD clinical section (must be assigned)
 *     ANY    + COMPLETED           → 400 (use add-nurse-note for notes)
 *
 *   Dental:
 *     NURSE  + CREATED_BY_NURSE    → edit nurse vitals section
 *     DMD    + PENDING_DMD_REVIEW  → edit DMD clinical section (must be assigned)
 *     ANY    + COMPLETED           → 400
 *
 * WHY EDITS ARE SEPARATE FROM WORKFLOW COMPLETION STEPS:
 *   Completion steps (complete-md-section, complete-dmd-section) advance the
 *   workflow state. Edits correct mistakes without changing state. A NURSE
 *   who mistyped a temperature can fix it before assigning; an MD can revise
 *   the diagnosis before it transitions to PENDING_NURSE_REVIEW.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitEditService {

    private final VisitManagementRepository visitManagementRepository;
    private final AuditLogService auditLogService;

    // MEDICAL VISIT EDIT

    @Transactional
    public MedicalVisitResponse editMedicalVisit(
        Long visitId,
        MedicalVisitEditRequest req,
        Authentication auth
    ) {
        String username = auth.getName();
        String role = primaryRole(auth);

        Visits base = loadVisit(visitId);

        if (!(base instanceof MedicalVisits visit)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Visit " + visitId + " is not a medical visit."
            );
        }

        VisitStatus status = visit.getStatus();

        // COMPLETED is always read-only
        if (status == VisitStatus.COMPLETED) {
            throw new InvalidStateTransitionException(
                visitId,
                status,
                status,
                "Cannot edit a completed visit. " +
                    "Use POST /api/visits/medical/" +
                    visitId +
                    "/add-nurse-note to add notes."
            );
        }

        // Apply edits based on role + status
        switch (role) {
            case "ROLE_NURSE" -> {
                if (status != VisitStatus.CREATED_BY_NURSE) {
                    throw new InvalidStateTransitionException(
                        visitId,
                        status,
                        status,
                        "NURSE can only edit a medical visit in CREATED_BY_NURSE status. " +
                            "Current status: " +
                            status.name()
                    );
                }
                applyNurseMedicalFields(visit, req);
            }
            case "ROLE_MD" -> {
                if (status != VisitStatus.PENDING_MD_REVIEW) {
                    throw new InvalidStateTransitionException(
                        visitId,
                        status,
                        status,
                        "MD can only edit a medical visit in PENDING_MD_REVIEW status. " +
                            "Current status: " +
                            status.name()
                    );
                }
                validateAssignment(visit, visitId, userId(auth));
                applyMdFields(visit, req);
            }
            default -> throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Role " + role + " is not permitted to edit medical visits."
            );
        }

        visitManagementRepository.save(visit);

        auditLogService.record(
            "MedicalVisits",
            Math.toIntExact(visitId),
            "VISIT_EDITED",
            "Medical visit edited by " + role + " in status " + status.name()
        );

        log.info(
            "Medical visit edited: id={}, by={}, role={}, status={}",
            visitId,
            username,
            role,
            status
        );
        return toMedicalResponse(visit);
    }

    // DENTAL VISIT EDIT

    @Transactional
    public DentalVisitResponse editDentalVisit(
        Long visitId,
        DentalVisitEditRequest req,
        Authentication auth
    ) {
        String username = auth.getName();
        String role = primaryRole(auth);

        Visits base = loadVisit(visitId);

        if (!(base instanceof DentalVisits visit)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Visit " + visitId + " is not a dental visit."
            );
        }

        VisitStatus status = visit.getStatus();

        if (status == VisitStatus.COMPLETED) {
            throw new InvalidStateTransitionException(
                visitId,
                status,
                status,
                "Cannot edit a completed dental visit."
            );
        }

        switch (role) {
            case "ROLE_NURSE" -> {
                if (status != VisitStatus.CREATED_BY_NURSE) {
                    throw new InvalidStateTransitionException(
                        visitId,
                        status,
                        status,
                        "NURSE can only edit a dental visit in CREATED_BY_NURSE status. " +
                            "Current status: " +
                            status.name()
                    );
                }
                applyNurseDentalFields(visit, req);
            }
            case "ROLE_DMD" -> {
                if (status != VisitStatus.PENDING_DMD_REVIEW) {
                    throw new InvalidStateTransitionException(
                        visitId,
                        status,
                        status,
                        "DMD can only edit a dental visit in PENDING_DMD_REVIEW status. " +
                            "Current status: " +
                            status.name()
                    );
                }
                validateAssignment(visit, visitId, userId(auth));
                applyDmdFields(visit, req);
            }
            default -> throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Role " + role + " is not permitted to edit dental visits."
            );
        }

        visitManagementRepository.save(visit);

        auditLogService.record(
            "DentalVisits",
            Math.toIntExact(visitId),
            "VISIT_EDITED",
            "Dental visit edited by " + role + " in status " + status.name()
        );

        log.info(
            "Dental visit edited: id={}, by={}, role={}, status={}",
            visitId,
            username,
            role,
            status
        );
        return toDentalResponse(visit);
    }

    // FIELD APPLICATORS

    /**
     * Applies only the NURSE-owned vitals to a medical visit.
     * MD clinical fields in the request are silently ignored — not an error,
     * since the frontend sends the full payload and the service filters.
     */
    private void applyNurseMedicalFields(
        MedicalVisits v,
        MedicalVisitEditRequest req
    ) {
        if (req.chiefComplaint() != null) v.setChiefComplaint(
            req.chiefComplaint()
        );
        if (req.temperature() != null) v.setTemperature(req.temperature());
        if (req.bloodPressure() != null) v.setBloodPressure(
            req.bloodPressure()
        );
        if (req.pulseRate() != null) v.setPulseRate(req.pulseRate());
        if (req.respiratoryRate() != null) v.setRespiratoryRate(
            req.respiratoryRate()
        );
        if (req.spo2() != null) v.setSpo2(req.spo2());
        if (req.weight() != null) v.setWeight(req.weight());
        if (req.height() != null) v.setHeight(req.height());
    }

    /** Applies only the MD-owned clinical section fields. */
    private void applyMdFields(MedicalVisits v, MedicalVisitEditRequest req) {
        if (req.history() != null) v.setHistory(req.history());
        if (req.physicalExamFindings() != null) v.setPhysicalExamFindings(
            req.physicalExamFindings()
        );
        if (req.diagnosis() != null) v.setDiagnosis(req.diagnosis());
        if (req.plan() != null) v.setPlan(req.plan());
        if (req.treatment() != null) v.setTreatment(req.treatment());
        if (req.diagnosticTestResult() != null) v.setDiagnosticTestResult(
            req.diagnosticTestResult()
        );
        if (req.hama() != null) v.setHama(req.hama());
        if (req.referralForm() != null) v.setReferralForm(req.referralForm());
        if (req.diagnosticTestImage() != null) v.setDiagnosticTestImage(
            req.diagnosticTestImage()
        );
        if (req.medicalChartImage() != null) v.setMedicalChartImage(
            req.medicalChartImage()
        );
    }

    /** Applies only NURSE-owned vitals to a dental visit. */
    private void applyNurseDentalFields(
        DentalVisits v,
        DentalVisitEditRequest req
    ) {
        if (req.chiefComplaint() != null) v.setChiefComplaint(
            req.chiefComplaint()
        );
        if (req.temperature() != null) v.setTemperature(req.temperature());
        if (req.bloodPressure() != null) v.setBloodPressure(
            req.bloodPressure()
        );
        if (req.pulseRate() != null) v.setPulseRate(req.pulseRate());
    }

    /** Applies only DMD-owned clinical section fields. */
    private void applyDmdFields(DentalVisits v, DentalVisitEditRequest req) {
        if (req.dentalNotes() != null) v.setDentalNotes(req.dentalNotes());
        if (req.treatmentProvided() != null) v.setTreatmentProvided(
            req.treatmentProvided()
        );
        if (req.toothInvolved() != null) v.setToothInvolved(
            req.toothInvolved()
        );
        if (req.diagnosis() != null) v.setDiagnosis(req.diagnosis());
        if (req.plan() != null) v.setPlan(req.plan());
        if (req.referralForm() != null) v.setReferralForm(req.referralForm());
        if (req.dentalChartImage() != null) v.setDentalChartImage(
            req.dentalChartImage()
        );
    }

    // PRIVATE HELPERS

    private Visits loadVisit(Long visitId) {
        return visitManagementRepository
            .findByIdWithPatient(visitId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Visit with id " + visitId + " not found."
                )
            );
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
                "Only the assigned clinic staff can edit this visit."
            );
        }
    }

    private String primaryRole(Authentication auth) {
        return auth
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a != null && a.startsWith("ROLE_"))
            .findFirst()
            .orElse("ROLE_UNKNOWN");
    }

    private Long userId(Authentication auth) {
        if (
            auth.getPrincipal() instanceof UserPrincipal up
        ) return (long) up.getId();
        throw new IllegalStateException(
            "Cannot extract user ID from authentication."
        );
    }

    private MedicalVisitResponse toMedicalResponse(MedicalVisits v) {
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
            v.getChiefComplaint(),
            v.getTemperature(),
            v.getBloodPressure(),
            v.getPulseRate(),
            v.getRespiratoryRate(),
            v.getSpo2(),
            v.getHistory(),
            v.getPhysicalExamFindings(),
            v.getDiagnosis(),
            v.getPlan(),
            v.getTreatment(),
            v.getDiagnosticTestResult(),
            v.getHama(),
            v.getReferralForm(),
            notes,
            v.getCreatedBy(),
            v.getCreatedAt(),
            v.getAssignedToUserId(),
            v.getAssignedAt(),
            v.getCompletedAt()
        );
    }

    private DentalVisitResponse toDentalResponse(DentalVisits v) {
        return new DentalVisitResponse(
            v.getId(),
            v.getStatus(),
            (long) v.getPatient().getId(),
            v.getPatient().getName(),
            v.getVisitDate(),
            v.getChiefComplaint(),
            v.getTemperature(),
            v.getBloodPressure(),
            v.getPulseRate(),
            v.getDiagnosis(),
            v.getDentalNotes(),
            v.getTreatmentProvided(),
            v.getToothInvolved(),
            v.getPlan(),
            v.getReferralForm(),
            v.getCreatedBy(),
            v.getCreatedAt(),
            v.getAssignedToUserId(),
            v.getAssignedAt(),
            v.getCompletedAt()
        );
    }
}
