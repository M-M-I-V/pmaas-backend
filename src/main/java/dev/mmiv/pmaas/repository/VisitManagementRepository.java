package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.Visits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the visit management operations (list, edit, import, export).
 *
 * This is separate from VisitRepositories (which is workflow-focused) so that
 * the read/write concerns remain distinct. The two repositories share the same
 * underlying JpaRepository<Visits, Long> — Spring Data allows multiple
 * repository interfaces for the same entity.
 *
 * WHY JOIN FETCH v.patient:
 *   MedicalVisits and DentalVisits both reference Patients with FetchType.LAZY.
 *   Without JOIN FETCH, loading 100 visits would issue 101 queries (1 for the
 *   list + 1 per patient). JOIN FETCH collapses this to 1 query.
 *
 * WHY JOINED INHERITANCE WORKS HERE:
 *   "FROM Visits v" with JOINED inheritance causes Hibernate to issue a SQL
 *   query with LEFT OUTER JOINs to medical_visits and dental_visits. The result
 *   set contains properly typed MedicalVisits and DentalVisits instances that
 *   service-layer instanceof checks handle correctly.
 */
@Repository
public interface VisitManagementRepository extends JpaRepository<Visits, Long> {

    /**
     * All visits with their patient, ordered newest first.
     * Used by the /visits-list endpoint (full visits table).
     */
    @Query("""
        SELECT v FROM Visits v
        JOIN FETCH v.patient
        ORDER BY v.visitDate DESC, v.createdAt DESC
        """)
    List<Visits> findAllWithPatient();

    /**
     * All visits for a specific patient, ordered newest first.
     * Used by the /visits-list/patient/{id} endpoint (per-patient history).
     */
    @Query("""
        SELECT v FROM Visits v
        JOIN FETCH v.patient p
        WHERE p.id = :patientId
        ORDER BY v.visitDate DESC, v.createdAt DESC
        """)
    List<Visits> findAllWithPatientByPatientId(@Param("patientId") Long patientId);

    /**
     * Single visit with patient eagerly loaded.
     * Used by the edit service to avoid LazyInitializationException when
     * accessing patient.getName() outside the loading transaction.
     */
    @Query("""
        SELECT v FROM Visits v
        JOIN FETCH v.patient
        WHERE v.id = :id
        """)
    Optional<Visits> findByIdWithPatient(@Param("id") Long id);
}