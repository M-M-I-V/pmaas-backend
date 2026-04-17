package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.*;
import dev.mmiv.pmaas.entity.*;
import dev.mmiv.pmaas.entity.InventoryItem;
import dev.mmiv.pmaas.exception.InsufficientInventoryException;
import dev.mmiv.pmaas.exception.InvalidStateTransitionException;
import dev.mmiv.pmaas.repository.InventoryItemRepository;
import dev.mmiv.pmaas.repository.PrescriptionRepository;
import dev.mmiv.pmaas.repository.VisitsRepository;
import dev.mmiv.pmaas.service.AuditLogService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles atomic inventory deduction when a pmaasian prescribes medicines.
 *
 * ATOMICITY CONTRACT:
 *   1. Load ALL inventory items — fail fast if any item ID is invalid
 *   2. Validate ALL stock levels — fail fast if any item is insufficient
 *   3. Deduct ALL items — only if steps 1 and 2 pass completely
 *
 * If step 2 fails for item N, NO inventory is modified for items 1..N-1.
 * The entire operation is enclosed in @Transactional — any exception triggers
 * a full rollback. This is not a best-effort partial deduction.
 *
 * SECURITY NOTE:
 *   The calling controller enforces that only MD/DMD with assignment can reach
 *   this method. The service additionally checks visit.isPrescribeAllowed() so
 *   prescriptions cannot be written to a visit in CREATED_BY_NURSE status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final VisitsRepository visitsRepository;
    private final InventoryItemRepository inventoryRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public PrescriptionResultResponse prescribe(
        Long visitId,
        PrescriptionRequest req,
        Authentication auth
    ) {
        String username = auth.getName();

        // Load visit
        Visits visit = visitsRepository
            .findByIdWithPatient(visitId)
            .orElseThrow(() ->
                new dev.mmiv.pmaas.exception.PatientNotFoundException(visitId)
            );

        if (!visit.getStatus().isPrescribeAllowed()) {
            throw new InvalidStateTransitionException(
                visitId,
                visit.getStatus(),
                visit.getStatus(),
                "Prescriptions cannot be written when visit status is " +
                    visit.getStatus().name() +
                    "."
            );
        }

        // Phase 1: Load all inventory items
        // Fail immediately if any item ID does not exist.
        record ItemPair(PrescriptionLineItem req, InventoryItem item) {}
        List<ItemPair> pairs = new ArrayList<>();

        for (PrescriptionLineItem line : req.prescriptions()) {
            InventoryItem item = inventoryRepository
                .findById(line.inventoryItemId())
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Inventory item with ID " +
                            line.inventoryItemId() +
                            " not found."
                    )
                );
            pairs.add(new ItemPair(line, item));
        }

        // Phase 2: Validate ALL stock levels before ANY deduction
        for (ItemPair pair : pairs) {
            int available =
                pair.item().getStockOnHand() != null
                    ? pair.item().getStockOnHand()
                    : 0;
            int requested = pair.req().quantity();

            if (requested <= 0) {
                throw new IllegalArgumentException(
                    "Quantity for '" +
                        pair.item().getItemName() +
                        "' must be greater than zero."
                );
            }

            if (available < requested) {
                throw new InsufficientInventoryException(
                    pair.item().getId(),
                    pair.item().getItemName(),
                    requested,
                    available
                );
            }
        }

        // ── Phase 3: Deduct inventory and create prescription records ──────────
        // Only reached if Phase 2 passed for ALL items.
        List<PrescriptionLineResult> results = new ArrayList<>();
        StringJoiner auditDetails = new StringJoiner(", ");

        for (ItemPair pair : pairs) {
            InventoryItem item = pair.item();
            int previousStock = item.getStockOnHand();
            int newStock = previousStock - pair.req().quantity();

            item.setStockOnHand(newStock);
            inventoryRepository.save(item);

            // Create immutable prescription record
            Prescription prescription = new Prescription(
                visit,
                item,
                pair.req().quantity(),
                previousStock,
                newStock,
                pair.req().reason(),
                username
            );
            prescriptionRepository.save(prescription);

            results.add(
                new PrescriptionLineResult(
                    item.getId(),
                    item.getItemName(),
                    pair.req().quantity(),
                    previousStock,
                    newStock,
                    "SUCCESS"
                )
            );

            auditDetails.add(
                item.getItemName() + " (" + pair.req().quantity() + " units)"
            );
        }

        // Audit log
        String entityName =
            visit instanceof MedicalVisits ? "MedicalVisits" : "DentalVisits";
        auditLogService.record(
            entityName,
            Math.toIntExact(visitId),
            "MEDICINE_PRESCRIBED",
            "Prescribed: " + auditDetails + ", stock deducted from inventory"
        );

        log.info(
            "Prescription complete: visitId={}, items={}, by={}",
            visitId,
            results.size(),
            username
        );

        return new PrescriptionResultResponse(
            visitId,
            results,
            "All medicines successfully deducted from inventory",
            LocalDateTime.now(),
            username
        );
    }
}
