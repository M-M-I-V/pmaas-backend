package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.VisitsList;
import dev.mmiv.pmaas.entity.DentalVisits;
import dev.mmiv.pmaas.entity.MedicalVisits;
import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.entity.Visits;
import dev.mmiv.pmaas.repository.VisitManagementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only service for visit list views.
 *
 * CHANGE: toVisitsList() now includes assignedToUserId in the VisitsList record.
 * This field is required by the frontend "My Queue" filter so that MD and DMD
 * users can see only the visits assigned to them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitReadService {

    private final VisitManagementRepository visitManagementRepository;

    public List<VisitsList> getVisitsList() {
        return visitManagementRepository
            .findAllWithPatient()
            .stream()
            .map(this::toVisitsList)
            .toList();
    }

    public List<VisitsList> getVisitsListByPatientId(Long patientId) {
        return visitManagementRepository
            .findAllWithPatientByPatientId(patientId)
            .stream()
            .map(this::toVisitsList)
            .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Maps a polymorphic Visits entity to the flat VisitsList DTO.
     *
     * All shared clinical fields (chiefComplaint, physicalExamFindings,
     * diagnosis, treatment) are on the base Visits entity after V13, so
     * they are read directly via inherited getters with no instanceof needed.
     *
     * assignedToUserId is passed through from the base entity and will be
     * null for any visit still in CREATED_BY_NURSE status.
     */
    private VisitsList toVisitsList(Visits v) {
        return new VisitsList(
            v.getId(),
            buildFullName(v.getPatient()),
            v.getPatient() != null ? v.getPatient().getBirthDate() : null,
            v.getVisitDate(),
            v.getVisitType() != null ? v.getVisitType().name() : null,
            v.getChiefComplaint(),
            v.getPhysicalExamFindings(),
            v.getDiagnosis(),
            v.getTreatment(),
            v.getStatus(),
            v.getAssignedToUserId() // null until NURSE assigns the visit
        );
    }

    private String buildFullName(Patients p) {
        if (p == null) return "";
        String mi = (p.getMiddleInitial() != null &&
            !p.getMiddleInitial().isBlank())
            ? " " + p.getMiddleInitial() + "."
            : "";
        return p.getFirstName() + mi + " " + p.getLastName();
    }
}
