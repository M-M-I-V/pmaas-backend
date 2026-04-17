package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.*;
import dev.mmiv.pmaas.service.PrescriptionService;
import dev.mmiv.pmaas.service.VisitWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Unified Visit Workflow Controller.
 *
 * Implements all workflow step endpoints. Each endpoint:
 *   1. Enforces role via @PreAuthorize (Spring Security)
 *   2. Enforces assignment ownership via @visitAccessValidator SpEL
 *   3. Delegates all business logic to VisitWorkflowService
 *   4. Returns structured DTOs — no entity exposure
 *
 * ADMIN EXCLUSION: No endpoint in this controller includes 'ADMIN' in
 * any @PreAuthorize expression. ADMINs cannot access visit data by design.
 *
 * BASE PATH: /api/visits
 */
@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
public class VisitsController {

    private final VisitWorkflowService workflowService;
    private final PrescriptionService  prescriptionService;

    // STEP 1 — NURSE CREATES INITIAL VISIT

    /**
     * POST /api/visits/medical/create-initial
     *
     * Creates a new medical visit in CREATED_BY_NURSE status.
     * Only NURSE may call this endpoint. MD, DMD, and ADMIN are excluded.
     */
    @PostMapping("/medical/create-initial")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('NURSE')")
    public VisitSummaryResponse createMedicalVisit(
            @Valid @RequestBody MedicalVisitCreateRequest request,
            Authentication auth
    ) {
        return workflowService.createMedicalVisit(request, auth);
    }

    /**
     * POST /api/visits/dental/create-initial
     *
     * Creates a new dental visit in CREATED_BY_NURSE status.
     */
    @PostMapping("/dental/create-initial")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('NURSE')")
    public VisitSummaryResponse createDentalVisit(
            @Valid @RequestBody DentalVisitCreateRequest request,
            Authentication auth
    ) {
        return workflowService.createDentalVisit(request, auth);
    }

    // STEP 2 — NURSE ASSIGNS TO MD OR DMD

    /**
     * POST /api/visits/{visitId}/assign
     *
     * Assigns the visit to an MD (medical) or DMD (dental).
     * Only NURSE may assign. The service validates that the assignee role
     * matches the visit type.
     *
     * Transitions:
     *   Medical: CREATED_BY_NURSE → PENDING_MD_REVIEW
     *   Dental:  CREATED_BY_NURSE → PENDING_DMD_REVIEW
     */
    @PostMapping("/{visitId}/assign")
    @PreAuthorize("hasRole('NURSE')")
    public VisitSummaryResponse assignVisit(
            @PathVariable Long visitId,
            @Valid @RequestBody VisitAssignRequest request,
            Authentication auth
    ) {
        return workflowService.assignVisit(visitId, request, auth);
    }

    // STEP 3a — MD COMPLETES MEDICAL SECTION

    /**
     * PUT /api/visits/medical/{visitId}/complete-md-section
     *
     * Only the MD assigned to this specific visit may call this endpoint.
     * @PreAuthorize checks:
     *   1. Role must be MD
     *   2. Authenticated user must be the assignedToUserId (via SpEL bean)
     *
     * Transitions: PENDING_MD_REVIEW → PENDING_NURSE_REVIEW
     */
    @PutMapping("/medical/{visitId}/complete-md-section")
    @PreAuthorize("hasRole('MD') and @visitAccessValidator.isAssignedMd(#visitId, authentication)")
    public MedicalVisitResponse completeMdSection(
            @PathVariable Long visitId,
            @Valid @RequestBody MedicalVisitCompletionRequest request,
            Authentication auth
    ) {
        return workflowService.completeMdSection(visitId, request, auth);
    }

    // STEP 3b — DMD COMPLETES DENTAL SECTION

    /**
     * PUT /api/visits/dental/{visitId}/complete-dmd-section
     *
     * Only the DMD assigned to this specific visit may call this endpoint.
     * Transitions: PENDING_DMD_REVIEW → COMPLETED
     */
    @PutMapping("/dental/{visitId}/complete-dmd-section")
    @PreAuthorize("hasRole('DMD') and @visitAccessValidator.isAssignedDmd(#visitId, authentication)")
    public DentalVisitResponse completeDmdSection(
            @PathVariable Long visitId,
            @Valid @RequestBody DentalVisitCompletionRequest request,
            Authentication auth
    ) {
        return workflowService.completeDmdSection(visitId, request, auth);
    }

    // STEP 4 — NURSE ADDS TIMESTAMPED NOTE (medical only)

    /**
     * POST /api/visits/medical/{visitId}/add-nurse-note
     *
     * Adds an immutable timestamped note. First note in PENDING_NURSE_REVIEW
     * transitions the visit to COMPLETED. Subsequent calls on COMPLETED visits
     * append additional notes.
     *
     * NURSE role only. The service validates the visit status allows note addition.
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

    // PRESCRIBE — MD OR DMD PRESCRIBES MEDICINES

    /**
     * POST /api/visits/{visitId}/prescribe
     *
     * Prescribes medicines and atomically deducts them from inventory.
     * Both MD (assigned to this visit) and DMD (assigned to this visit) may
     * call this endpoint.
     *
     * If ANY medicine lacks sufficient stock, the entire operation is rejected.
     * No partial deductions occur.
     */
    @PostMapping("/{visitId}/prescribe")
    @PreAuthorize("@visitAccessValidator.isAssignedClinician(#visitId, authentication)")
    public PrescriptionResultResponse prescribe(
            @PathVariable Long visitId,
            @Valid @RequestBody PrescriptionRequest request,
            Authentication auth
    ) {
        return prescriptionService.prescribe(visitId, request, auth);
    }
}