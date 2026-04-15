package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.ContactFilterRequest;
import dev.mmiv.pmaas.dto.ContactRequest;
import dev.mmiv.pmaas.dto.ContactResponse;
import dev.mmiv.pmaas.dto.ImportResponse;
import dev.mmiv.pmaas.service.ContactsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for the Contacts module.
 *
 * Security:
 *   - Read access: all authenticated clinic roles (MD, DMD, NURSE, ADMIN)
 *   - Write access: clinical roles only (MD, DMD, NURSE)
 *   - Delete access: ADMIN only
 *   - Import/export: clinical roles and ADMIN
 *
 * All filtering is handled by the backend via query parameters bound
 * to ContactFilterRequest. No filtering logic lives in the frontend.
 */
@Slf4j
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactsController {

    private final ContactsService contactsService;

    // GET /api/contacts

    /**
     * Returns a paginated, filtered list of contacts.
     *
     * All query parameters are optional and combinable. When all are omitted,
     * returns all contacts ordered by contactDate DESC, contactTime DESC.
     *
     * Example:
     *   GET /api/contacts?name=juan&visitType=MEDICAL&fromDate=2025-01-01
     *                    &toDate=2025-12-31&page=0&size=10&sort=contactDate,desc
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE', 'ADMIN')")
    public Page<ContactResponse> findAll(
        @ModelAttribute ContactFilterRequest filter,
        @PageableDefault(
            size = 10,
            sort = "contactDate",
            direction = Sort.Direction.DESC
        ) Pageable pageable
    ) {
        return contactsService.findAll(filter, pageable);
    }

    // GET /api/contacts/{id}

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE', 'ADMIN')")
    public ContactResponse findById(@PathVariable Long id) {
        return contactsService.findById(id);
    }

    // POST /api/contacts

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ContactResponse create(@Valid @RequestBody ContactRequest request) {
        return contactsService.create(request);
    }

    // PUT /api/contacts/{id}

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public ContactResponse update(
        @PathVariable Long id,
        @Valid @RequestBody ContactRequest request
    ) {
        return contactsService.update(id, request);
    }

    // DELETE /api/contacts/{id}

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        contactsService.delete(id);
    }

    // POST /api/contacts/import

    /**
     * Accepts a .xlsx file upload and imports rows from the
     * "Contacts Log 2025" sheet. Returns a summary of the import.
     *
     * Example response:
     * {
     *   "totalRows": 120,
     *   "imported": 115,
     *   "skipped": 2,
     *   "failed": 3,
     *   "errors": [
     *     "Row 12: Invalid or missing date in column A.",
     *     "Row 45: Missing name in column C.",
     *     "Row 87: Unknown visit type 'CARDIO'. Expected MEDICAL or DENTAL."
     *   ]
     * }
     */
    @PostMapping(
        value = "/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE', 'ADMIN')")
    public ImportResponse importContacts(
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        log.info(
            "Import requested — filename={}, size={}",
            file.getOriginalFilename(),
            file.getSize()
        );
        return contactsService.importFromExcel(file);
    }

    // GET /api/contacts/export

    /**
     * Exports filtered contacts to an .xlsx file.
     * Accepts the same filter parameters as GET /api/contacts.
     * Streams the response directly — no intermediate storage.
     *
     * Example:
     *   GET /api/contacts/export?visitType=MEDICAL&fromDate=2025-01-01&toDate=2025-06-30
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE', 'ADMIN')")
    public void exportContacts(
        @ModelAttribute ContactFilterRequest filter,
        HttpServletResponse response
    ) throws IOException {
        log.info("Export requested with filters: {}", filter);
        contactsService.exportToExcel(filter, response);
    }
}
