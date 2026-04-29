package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.VisitTypeResponse;
import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.*;
import dev.mmiv.pmaas.entity.DentalVisits;
import dev.mmiv.pmaas.entity.MedicalVisits;
import dev.mmiv.pmaas.entity.Visits;
import dev.mmiv.pmaas.repository.VisitsRepository;
import dev.mmiv.pmaas.service.PrescriptionService;
import dev.mmiv.pmaas.service.VisitWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unified Visit Workflow Controller.
 *
 * ADDED: GET /api/visits/{visitId}/type
 *   Lightweight type-resolver endpoint. Returns visitId, visitType, and status
 *   only — no clinical data. Used by the frontend when navigating to
 *   /visits/{id} without a ?type= query parameter.
 *
 * ADDED: GET /api/visits/medical/{visitId}, GET /api/visits/dental/{visitId}
 *   Full detail GET endpoints for individual visits. Were previously missing;
 *   the frontend's edit forms and detail page require them.
 *
 * All other endpoints unchanged from the previous version.
 *
 * ADMIN EXCLUSION: No endpoint includes 'ADMIN' in @PreAuthorize.
 * BASE PATH: /api/visits
 */
@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
public class VisitsController {

    private final VisitWorkflowService workflowService;
    private final PrescriptionService prescriptionService;
    private final VisitsRepository visitsRepository;

    // ══════════════════════════════════════════════════════════════════════════
    // TYPE RESOLVER — frontend fallback when ?type= param is absent
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/visits/{visitId}/type
     *
     * Returns the visitType and current status for a visit without returning
     * any clinical (PHI) data. The frontend calls this endpoint when the user
     * navigates to /visits/{id} without a ?type= query parameter so it can
     * determine which full-detail endpoint to call next.
     *
     * Example response:
     * {
     *   "visitId": 42,
     *   "visitType": "MEDICAL",
     *   "status": "PENDING_MD_REVIEW"
     * }
     *
     * Returns 404 if the visit does not exist.
     * All three clinical roles may call this endpoint.
     */
    @GetMapping("/{visitId}/type")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public VisitTypeResponse getVisitType(@PathVariable Long visitId) {
        Visits visit = visitsRepository
            .findById(visitId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Visit with id " + visitId + " not found."
                )
            );

        return new VisitTypeResponse(
            visit.getId(),
            visit.getVisitType(),
            visit.getStatus()
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 1 — NURSE CREATES INITIAL VISIT
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/medical/create-initial")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('NURSE')")
    public VisitSummaryResponse createMedicalVisit(
        @Valid @RequestBody MedicalVisitCreateRequest request,
        Authentication auth
    ) {
        return workflowService.createMedicalVisit(request, auth);
    }

    @PostMapping("/dental/create-initial")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('NURSE')")
    public VisitSummaryResponse createDentalVisit(
        @Valid @RequestBody DentalVisitCreateRequest request,
        Authentication auth
    ) {
        return workflowService.createDentalVisit(request, auth);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET BY ID — single visit full detail
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/visits/medical/{visitId}
     *
     * Returns the full detail of a medical visit.
     * Returns 404 if not found; 400 if the visit exists but is not medical.
     */
    @GetMapping("/medical/{visitId}")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public MedicalVisitResponse getMedicalVisit(@PathVariable Long visitId) {
        Visits base = visitsRepository
            .findByIdWithPatient(visitId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Visit with id " + visitId + " not found."
                )
            );

        if (!(base instanceof MedicalVisits visit)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Visit " +
                    visitId +
                    " is not a medical visit. " +
                    "Use GET /api/visits/dental/" +
                    visitId +
                    " for dental visits."
            );
        }
        return workflowService.toMedicalResponse(visit);
    }

    /**
     * GET /api/visits/dental/{visitId}
     *
     * Returns the full detail of a dental visit.
     * Returns 404 if not found; 400 if the visit exists but is not dental.
     */
    @GetMapping("/dental/{visitId}")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public DentalVisitResponse getDentalVisit(@PathVariable Long visitId) {
        Visits base = visitsRepository
            .findByIdWithPatient(visitId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Visit with id " + visitId + " not found."
                )
            );

        if (!(base instanceof DentalVisits visit)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Visit " +
                    visitId +
                    " is not a dental visit. " +
                    "Use GET /api/visits/medical/" +
                    visitId +
                    " for medical visits."
            );
        }
        return workflowService.toDentalResponse(visit);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 2 — NURSE ASSIGNS TO MD OR DMD
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/{visitId}/assign")
    @PreAuthorize("hasRole('NURSE')")
    public VisitSummaryResponse assignVisit(
        @PathVariable Long visitId,
        @Valid @RequestBody VisitAssignRequest request,
        Authentication auth
    ) {
        return workflowService.assignVisit(visitId, request, auth);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 3a — MD COMPLETES MEDICAL SECTION
    // ══════════════════════════════════════════════════════════════════════════

    @PutMapping("/medical/{visitId}/complete-md-section")
    @PreAuthorize(
        "hasRole('MD') and @visitAccessValidator.isAssignedMd(#visitId, authentication)"
    )
    public MedicalVisitResponse completeMdSection(
        @PathVariable Long visitId,
        @Valid @RequestBody MedicalVisitCompletionRequest request,
        Authentication auth
    ) {
        return workflowService.completeMdSection(visitId, request, auth);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 3b — DMD COMPLETES DENTAL SECTION
    // ══════════════════════════════════════════════════════════════════════════

    @PutMapping("/dental/{visitId}/complete-dmd-section")
    @PreAuthorize(
        "hasRole('DMD') and @visitAccessValidator.isAssignedDmd(#visitId, authentication)"
    )
    public DentalVisitResponse completeDmdSection(
        @PathVariable Long visitId,
        @Valid @RequestBody DentalVisitCompletionRequest request,
        Authentication auth
    ) {
        return workflowService.completeDmdSection(visitId, request, auth);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STEP 4 — NURSE ADDS TIMESTAMPED NOTE (medical only)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/visits/medical/{visitId}/add-nurse-note
     *
     * Any NURSE may add a note — not restricted to the original creator.
     * This is intentional: a different nurse may be on shift when the patient
     * returns or when a follow-up note is needed.
     *
     * First note in PENDING_NURSE_REVIEW → COMPLETED.
     * Notes on already-COMPLETED visits are appended without changing status.
     */
    @PostMapping("/medical/{visitId}/add-nurse-note")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('NURSE')")
    public MedicalVisitResponse addNurseNote(
        @PathVariable Long visitId,
        @Valid @RequestBody NurseNoteRequest request,
        Authentication auth
    ) {
        return workflowService.addNurseNote(visitId, request, auth);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRESCRIBE
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/{visitId}/prescribe")
    @PreAuthorize(
        "@visitAccessValidator.isAssignedClinician(#visitId, authentication)"
    )
    public PrescriptionResultResponse prescribe(
        @PathVariable Long visitId,
        @Valid @RequestBody PrescriptionRequest request,
        Authentication auth
    ) {
        return prescriptionService.prescribe(visitId, request, auth);
    }
}
