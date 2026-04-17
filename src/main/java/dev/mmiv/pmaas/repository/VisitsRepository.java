package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.VisitStatus;
import dev.mmiv.pmaas.entity.Visits;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VisitsRepository extends JpaRepository<Visits, Long> {

    /**
     * Fetches a visit with its patient eagerly loaded.
     * Using JOIN FETCH prevents the N+1 query that would occur if
     * patient were accessed lazily on a detached entity.
     */
    @Query("""
        SELECT v FROM Visits v
        JOIN FETCH v.patient p
        WHERE v.id = :id
        """)
    Optional<Visits> findByIdWithPatient(@Param("id") Long id);

    /**
     * All visits assigned to a specific MD/DMD, paginated.
     * Used for role=MD and role=DMD dashboard list views.
     */
    @Query("""
        SELECT v FROM Visits v
        JOIN FETCH v.patient p
        WHERE v.assignedToUserId = :userId
        ORDER BY v.visitDate DESC
        """)
    Page<Visits> findByAssignedToUserId(
            @Param("userId") Long userId, Pageable pageable);

    /**
     * All visits in a given status, paginated.
     * Used by NURSE dashboard to show the queue.
     */
    @Query("""
        SELECT v FROM Visits v
        JOIN FETCH v.patient p
        WHERE v.status = :status
        ORDER BY v.visitDate DESC, v.createdAt DESC
        """)
    Page<Visits> findByStatus(
            @Param("status") VisitStatus status, Pageable pageable);

    /**
     * All visits for a given patient, paginated.
     */
    @Query("""
        SELECT v FROM Visits v
        WHERE v.patient.id = :patientId
        ORDER BY v.visitDate DESC
        """)
    Page<Visits> findByPatientId(
            @Param("patientId") Long patientId, Pageable pageable);
}
