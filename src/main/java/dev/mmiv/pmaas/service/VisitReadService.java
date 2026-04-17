package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.VisitsList;
import dev.mmiv.pmaas.entity.DentalVisits;
import dev.mmiv.pmaas.entity.MedicalVisits;
import dev.mmiv.pmaas.entity.Visits;
import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.repository.VisitManagementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read-only service for visit list views.
 *
 * Replaces VisitsService.getVisitsList() and getVisitsListByPatientId()
 * from the old codebase.
 *
 * WHY A SEPARATE SERVICE FROM VisitWorkflowService:
 * The workflow service owns state transitions. This service owns read
 * projections. Keeping them separate follows single-responsibility and
 * prevents the workflow service from becoming a god class.
 *
 * WHY SERVICE-LAYER MAPPING INSTEAD OF JPQL SELECT NEW:
 * With JOINED inheritance (Visits → MedicalVisits, DentalVisits), a JPQL
 * "SELECT new VisitsList(v.physicalExamFindings, ...)" query fails because
 * physicalExamFindings lives in the medical_visits table, not in visits.
 * Hibernate cannot project subtype columns into a DTO constructor from a
 * polymorphic FROM Visits query.
 *
 * The correct approach: load Visits (Hibernate automatically LEFT JOINs to
 * medical_visits and dental_visits via JOINED inheritance strategy), then
 * map in Java using instanceof pattern matching. This is exactly one SQL
 * query — not N+1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitReadService {

    private final VisitManagementRepository visitManagementRepository;

    /**
     * All visits across all patients, ordered by visitDate descending.
     * Used by the /visits table in the frontend.
     */
    public List<VisitsList> getVisitsList() {
        return visitManagementRepository.findAllWithPatient()
                .stream()
                .map(this::toVisitsList)
                .toList();
    }

    /**
     * All visits for a specific patient, ordered by visitDate descending.
     * Used by the /patients/{id} per-patient history table.
     */
    public List<VisitsList> getVisitsListByPatientId(Long patientId) {
        return visitManagementRepository.findAllWithPatientByPatientId(patientId)
                .stream()
                .map(this::toVisitsList)
                .toList();
    }

    // Private helpers

    /**
     * Maps a polymorphic Visits entity to the flat VisitsList DTO.
     *
     * Fields that only exist on one subtype (physicalExamFindings, treatment)
     * are null for the other type — the frontend already handles null on these
     * since they were always optional in the table display.
     *
     * treatment for dental: maps to treatmentProvided (closest equivalent).
     * diagnosis: present on both MedicalVisits and DentalVisits.
     * chiefComplaint: present on both subtypes.
     */
    private VisitsList toVisitsList(Visits v) {
        String chiefComplaint    = null;
        String physicalExam      = null;
        String diagnosis         = null;
        String treatment         = null;

        if (v instanceof MedicalVisits m) {
            chiefComplaint = m.getChiefComplaint();
            physicalExam   = m.getPhysicalExamFindings();
            diagnosis      = m.getDiagnosis();
            treatment      = m.getTreatment();
        } else if (v instanceof DentalVisits d) {
            chiefComplaint = d.getChiefComplaint();
            physicalExam   = null;              // dental has no physical exam field
            diagnosis      = d.getDiagnosis();
            treatment      = d.getTreatmentProvided();
        }

        return new VisitsList(
                v.getId(),
                buildFullName(v.getPatient()),
                v.getPatient() != null ? v.getPatient().getBirthDate() : null,
                v.getVisitDate(),
                v.getVisitType() != null ? v.getVisitType().name() : null,
                chiefComplaint,
                physicalExam,
                diagnosis,
                treatment,
                v.getStatus()
        );
    }

    private String buildFullName(Patients p) {
        if (p == null) return "";
        String mi = (p.getMiddleInitial() != null && !p.getMiddleInitial().isBlank())
                ? " " + p.getMiddleInitial() + "."
                : "";
        return p.getFirstName() + mi + " " + p.getLastName();
    }
}