package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.InventoryItemRequest;
import dev.mmiv.pmaas.dto.InventoryItemResponse;
import dev.mmiv.pmaas.dto.ImportResponse;
import dev.mmiv.pmaas.entity.ItemCategory;
import dev.mmiv.pmaas.service.InventoryExportService;
import dev.mmiv.pmaas.service.InventoryImportService;
import dev.mmiv.pmaas.service.InventoryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryImportService importService;
    private final InventoryExportService exportService;

    // Search & Read

    @GetMapping
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE', 'ADMIN')")
    public ResponseEntity<Page<InventoryItemResponse>> search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) ItemCategory category,
        @RequestParam(defaultValue = "0")        int page,
        @RequestParam(defaultValue = "20")       int size,
        @RequestParam(defaultValue = "itemName") String sortBy,
        @RequestParam(defaultValue = "asc")      String sortDir
    ) {
        return ResponseEntity.ok(
                inventoryService.search(q, category, page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE', 'ADMIN')")
    public ResponseEntity<InventoryItemResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getById(id));
    }

    // Create & Update

    @PostMapping
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<InventoryItemResponse> create(
        @Valid @RequestBody InventoryItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<InventoryItemResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody InventoryItemRequest request
    ) {
        return ResponseEntity.ok(inventoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        inventoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Excel Import

    /**
     * POST /api/inventory/import
     * Content-Type: multipart/form-data
     * Param: file (.xlsx, max 5 MB)
     *
     * Restricted to MD and NURSE — these roles manage inventory in the clinic.
     * Rate limiting for this endpoint is enforced at the reverse proxy level
     * (Caddy rate_limit directive) and can additionally be applied via a
     * Bucket4j filter if fine-grained per-user throttling is required.
     */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('MD', 'NURSE')")
    public ResponseEntity<ImportResponse> importExcel(
        @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(importService.importFromExcel(file));
    }

    // Excel Export

    /**
     * GET /api/inventory/export
     * Optional params: q (search), category (MEDICINE|SUPPLIES|CONSUMABLES)
     * Returns: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE', 'ADMIN')")
    public void exportExcel(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) ItemCategory category,
        HttpServletResponse response
    ) {
        exportService.exportToExcel(q, category, response);
    }
}