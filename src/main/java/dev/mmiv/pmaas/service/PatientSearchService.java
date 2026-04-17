package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.VisitWorkflowDTOs.PatientSearchItem;
import dev.mmiv.pmaas.repository.PatientsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Patient search service — case-insensitive partial match across
 * firstName, lastName, and studentNumber.
 *
 * The search query is delegated entirely to the database via JPA.
 * No in-memory filtering is performed. Results are paginated at the
 * database level via the Pageable argument.
 *
 * PRIVACY: The PatientSearchItem DTO returns only classification fields
 * (id, name, studentNumber, category, status). contactNumber is NOT
 * included in search results — callers must use GET /api/patients/{id}
 * to access contact details, which requires separate audit justification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientSearchService {

    private final PatientsRepository patientsRepository;

    private static final int MAX_QUERY_LENGTH = 100;

    public Page<PatientSearchItem> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Search query 'q' is required and must not be blank.");
        }

        String sanitized = query.trim();
        if (sanitized.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Search query must not exceed " + MAX_QUERY_LENGTH + " characters.");
        }

        log.debug("Patient search: query='{}', page={}", sanitized, pageable.getPageNumber());

        return patientsRepository.searchByNameOrStudentNumber(sanitized, pageable)
                .map(p -> new PatientSearchItem(
                        (long) p.getId(),
                        p.getFirstName(),
                        p.getLastName(),
                        p.getStudentNumber(),
                        p.getBirthDate() != null ? p.getBirthDate().toString() : null,
                        p.getCategory(),
                        p.getStatus()
                ));
    }
}