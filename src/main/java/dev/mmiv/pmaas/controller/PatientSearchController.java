package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.PatientSearchItem;
import dev.mmiv.pmaas.service.PatientSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient search controller.
 *
 * ADMIN is explicitly excluded. The system's ADMIN role manages users and
 * configuration — it should not have access to patient clinical records.
 *
 * The search results include only non-sensitive fields (name, student number,
 * category, status). Contact numbers and medical details are NOT returned here.
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientSearchController {

    private final PatientSearchService searchService;

    /**
     * GET /api/patients/search?q=juan&page=0&size=10
     *
     * Searches patients by partial first name, last name, or student number.
     * Case-insensitive. Results paginated by lastName, firstName ascending.
     *
     * Example:
     *   GET /api/patients/search?q=dela+cruz&page=0&size=10
     *   GET /api/patients/search?q=2024-001&page=0&size=5
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('NURSE', 'MD', 'DMD')")
    public Page<PatientSearchItem> search(
            @RequestParam String q,

            @PageableDefault(size = 10, sort = "lastName", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return searchService.search(q, pageable);
    }
}