package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.Patients;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Patients repository — includes the case-insensitive search query
 * required by the Visit Workflow module.
 *
 * Add this @Query method to your existing PatientsRepository.
 * If PatientsRepository already extends JpaRepository<Patients, Long>,
 * simply add the method below.
 */
@Repository
public interface PatientsRepository extends JpaRepository<Patients, Long> {
    /**
     * Case-insensitive partial match search across:
     *   - firstName
     *   - lastName
     *   - studentNumber
     *
     * Uses LOWER(...) LIKE LOWER(CONCAT('%', :q, '%')) per project conventions.
     * The query returns paginated results ordered by lastName, firstName for
     * consistent pagination across pages.
     *
     * Special LIKE characters in :q are NOT escaped here — this is intentional
     * for a search-box UX where '*' or '%' in the search term may be meaningful
     * to the user. If you want to prevent wildcard injection, add:
     *   .replace("%", "\\%").replace("_", "\\_")
     * to the sanitized query in PatientSearchService before passing it here.
     */
    @Query(
        """
        SELECT p FROM Patients p
        WHERE
          LOWER(p.firstName)     LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(p.lastName)   LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(p.studentNumber) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY p.lastName ASC, p.firstName ASC
        """
    )
    Page<Patients> searchByNameOrStudentNumber(
        @Param("q") String query,
        Pageable pageable
    );

    /**
     * Finds a patient by their student number.
     * Used during CSV import to check if a patient already exists.
     */
    Optional<Patients> findByStudentNumber(String studentNumber);
}
