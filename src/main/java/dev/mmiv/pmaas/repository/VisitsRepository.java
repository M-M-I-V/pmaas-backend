package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.dto.VisitsList;
import dev.mmiv.pmaas.entity.Visits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * All FUNCTION('MONTH'/'YEAR', ...) calls replaced with EXTRACT().
 * Why this matters:
 *   FUNCTION() is a JPQL escape hatch that delegates to the underlying database's
 *   native function by name. MySQL exposes MONTH() and YEAR(). PostgreSQL does not —
 *   it uses the SQL-standard EXTRACT(MONTH FROM ...) / EXTRACT(YEAR FROM ...) syntax.
 *   Using EXTRACT() here makes every query run correctly on both MySQL (current)
 *   and PostgreSQL (planned migration) without any code change at cutover.
 */
@Repository
public interface VisitsRepository extends JpaRepository<Visits, Integer>, JpaSpecificationExecutor<Visits> {

    @Query("SELECT COUNT(v) FROM Visits v WHERE v.visitDate = CURRENT_DATE")
    long countTodayVisits();

    // FUNCTION('MONTH',...) and FUNCTION('YEAR',...)
    @Query("SELECT COUNT(v) FROM Visits v " +
            "WHERE EXTRACT(MONTH FROM v.visitDate) = EXTRACT(MONTH FROM CURRENT_DATE) " +
            "AND EXTRACT(YEAR FROM v.visitDate)  = EXTRACT(YEAR FROM CURRENT_DATE)")
    long countMonthVisits();

    // same replacement in the GROUP BY aggregation query
    @Query("SELECT v.diagnosis, COUNT(v) " +
            "FROM Visits v " +
            "WHERE EXTRACT(MONTH FROM v.visitDate) = EXTRACT(MONTH FROM CURRENT_DATE) " +
            "AND EXTRACT(YEAR FROM v.visitDate)  = EXTRACT(YEAR FROM CURRENT_DATE) " +
            "GROUP BY v.diagnosis " +
            "ORDER BY COUNT(v) DESC")
    List<Object[]> countTopDiagnosesThisMonth();

    @Query("SELECT v.visitDate, COUNT(v) " +
            "FROM Visits v " +
            "WHERE v.visitDate >= :cutoffDate " +
            "GROUP BY v.visitDate " +
            "ORDER BY v.visitDate")
    List<Object[]> countVisitsTrendLast30Days(@Param("cutoffDate") LocalDate cutoffDate);

    @Query("SELECT v FROM Visits v JOIN FETCH v.patient")
    List<Visits> findAllWithPatient();

    @Query("""
        SELECT new dev.mmiv.pmaas.dto.VisitsList(
            v.id,
            CONCAT(p.firstName, ' ', p.lastName),
            p.birthDate,
            v.visitDate,
            CAST(v.visitType AS string),
            v.chiefComplaint,
            v.physicalExamFindings,
            v.diagnosis,
            v.treatment
        )
        FROM Visits v
        JOIN v.patient p
        WHERE p.id = :patientId
        ORDER BY v.visitDate DESC
    """)
    List<VisitsList> findVisitsListByPatientId(@Param("patientId") int patientId);
}