package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.VisitEditDTOs.DentalVisitEditRequest;
import dev.mmiv.pmaas.dto.VisitEditDTOs.MedicalVisitEditRequest;
import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.DentalVisitResponse;
import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.MedicalVisitResponse;
import dev.mmiv.pmaas.dto.VisitsList;
import dev.mmiv.pmaas.service.VisitEditService;
import dev.mmiv.pmaas.service.VisitImportExportService;
import dev.mmiv.pmaas.service.VisitReadService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Visit management endpoints — list, edit, import, export.
 *
 * These endpoints complement the workflow state machine in VisitsController.
 * They are read/write operations that do NOT advance the workflow status
 * (except during import, where all imported visits are set to COMPLETED).
 *
 * BASE PATH: /api (preserved from old VisitsController for frontend compatibility)
 *
 * PERMISSION SUMMARY:
 *   List endpoints:   NURSE, MD, DMD   (ADMIN excluded)
 *   Edit endpoints:   NURSE, MD, DMD   (service enforces status+assignment rules)
 *   Import:           NURSE, MD, DMD   (historical data migration)
 *   Export:           NURSE, MD, DMD
 *
 * DELETION: No DELETE endpoint exists. Visits are immutable once created.
 * Any edit to a completed visit is rejected by VisitEditService (400).
 * Audit logs are recorded for all edits.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VisitManagementController {

    private final VisitReadService readService;
    private final VisitEditService editService;
    private final VisitImportExportService importExportService;

    // ══════════════════════════════════════════════════════════════════════════
    // LIST ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/visits-list
     *
     * All visits ordered by visitDate descending.
     * Used by the /visits page visits table.
     *
     * The response includes the `status` field so the frontend can show
     * workflow state badges and conditionally enable/disable action buttons.
     *
     * Example response item:
     * {
     *   "id": 42,
     *   "fullName": "Maria Dela Cruz",
     *   "birthDate": "2000-05-15",
     *   "visitDate": "2026-04-16",
     *   "visitType": "MEDICAL",
     *   "chiefComplaint": "Headache",
     *   "physicalExamFindings": "BP elevated",
     *   "diagnosis": "Migraine",
     *   "treatment": "Aspirin 500mg",
     *   "status": "COMPLETED"
     * }
     */
    @GetMapping("/visits-list")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public ResponseEntity<List<VisitsList>> getVisitsList() {
        return ResponseEntity.ok(readService.getVisitsList());
    }

    /**
     * GET /api/visits-list/patient/{id}
     *
     * All visits for a specific patient, ordered by visitDate descending.
     * Used by the /patients/{id} per-patient history table.
     */
    @GetMapping("/visits-list/patient/{id}")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public ResponseEntity<List<VisitsList>> getVisitsListByPatient(
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(readService.getVisitsListByPatientId(id));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EDIT ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * PUT /api/visits/medical/{id}/edit
     *
     * Edits a medical visit in-place without advancing the workflow status.
     *
     * Which fields are applied depends on the caller's role and the visit's
     * current status — VisitEditService enforces this:
     *   NURSE  + CREATED_BY_NURSE   → vitals and chief complaint
     *   MD     + PENDING_MD_REVIEW  → clinical section (must be assigned)
     *   COMPLETED visits            → 400 Bad Request
     *
     * All edits are logged in the audit trail.
     */
    @PutMapping("/visits/medical/{id}/edit")
    @PreAuthorize("hasAnyRole('NURSE', 'MD')")
    public ResponseEntity<MedicalVisitResponse> editMedicalVisit(
        @PathVariable Long id,
        @RequestBody MedicalVisitEditRequest request,
        Authentication auth
    ) {
        return ResponseEntity.ok(
            editService.editMedicalVisit(id, request, auth)
        );
    }

    /**
     * PUT /api/visits/dental/{id}/edit
     *
     * Edits a dental visit in-place without advancing the workflow status.
     *
     *   NURSE  + CREATED_BY_NURSE    → vitals and chief complaint
     *   DMD    + PENDING_DMD_REVIEW  → clinical section (must be assigned)
     *   COMPLETED visits             → 400 Bad Request
     */
    @PutMapping("/visits/dental/{id}/edit")
    @PreAuthorize("hasAnyRole('NURSE', 'DMD')")
    public ResponseEntity<DentalVisitResponse> editDentalVisit(
        @PathVariable Long id,
        @RequestBody DentalVisitEditRequest request,
        Authentication auth
    ) {
        return ResponseEntity.ok(
            editService.editDentalVisit(id, request, auth)
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // IMPORT ENDPOINT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/visits/import
     *
     * Imports visits from a CSV file. All imported visits are set to COMPLETED
     * status (they represent historical records, not active workflow items).
     *
     * CSV format: produced by GET /api/visits/export (symmetric round-trip).
     * Column names must match the export headers exactly.
     */
    @PostMapping(
        value = "/visits/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public ResponseEntity<String> importVisits(
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        importExportService.importVisits(file);
        return ResponseEntity.ok("Visits imported successfully!");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPORT ENDPOINT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/visits/export
     *
     * Exports all visits to a CSV file with a UTF-8 BOM header so Excel
     * opens it with the correct encoding without manual configuration.
     *
     * Uses CSVPrinter which properly quotes fields containing commas,
     * preventing data corruption for clinical text fields like diagnosis.
     *
     * Nurse notes: all NurseNote entries for a medical visit are joined
     * with " | " into the "Nurse Notes" column.
     *
     * Status column is included in the export but ignored during import —
     * all imported visits are always set to COMPLETED.
     */
    @GetMapping("/visits/export")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public void exportVisits(HttpServletResponse response) throws IOException {
        importExportService.exportVisits(response);
    }
}
