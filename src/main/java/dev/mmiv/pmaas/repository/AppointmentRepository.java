package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.Appointment;
import dev.mmiv.pmaas.entity.AppointmentStatus;
import dev.mmiv.pmaas.entity.VisitType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository
    extends
        JpaRepository<Appointment, Long>,
        JpaSpecificationExecutor<Appointment>
{
    /**
     * Single appointment with patient and visit eagerly loaded.
     * Used by the detail and status-change service methods.
     */
    @Query(
        """
        SELECT a FROM Appointment a
        LEFT JOIN FETCH a.patient
        LEFT JOIN FETCH a.visit
        WHERE a.id = :id
        """
    )
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);

    /**
     * All appointments for a specific date, ordered by time.
     * Used by the daily calendar view.
     */
    @Query(
        """
        SELECT a FROM Appointment a
        LEFT JOIN FETCH a.patient
        WHERE a.appointmentDate = :date
        ORDER BY a.appointmentTime ASC NULLS LAST, a.createdAt ASC
        """
    )
    List<Appointment> findByDateWithPatient(@Param("date") LocalDate date);

    /**
     * Appointments in a date range.
     * Used by the weekly/monthly calendar and export.
     */
    @Query(
        """
        SELECT a FROM Appointment a
        LEFT JOIN FETCH a.patient
        WHERE a.appointmentDate BETWEEN :from AND :to
        ORDER BY a.appointmentDate ASC, a.appointmentTime ASC NULLS LAST
        """
    )
    List<Appointment> findByDateRangeWithPatient(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    /**
     * All appointments for a specific patient, newest first.
     * Used by the patient profile page.
     */
    @Query(
        """
        SELECT a FROM Appointment a
        WHERE a.patient.id = :patientId
        ORDER BY a.appointmentDate DESC, a.appointmentTime DESC NULLS LAST
        """
    )
    List<Appointment> findByPatientIdOrderByDateDesc(
        @Param("patientId") Long patientId
    );

    /**
     * Count appointments by date for dashboard chart.
     * Returns Object[] { LocalDate date, Long count }.
     * Used to replace the contacts-based daily appointments query.
     */
    @Query(
        """
        SELECT a.appointmentDate, COUNT(a)
        FROM Appointment a
        WHERE a.appointmentDate BETWEEN :from AND :to
          AND a.status <> dev.mmiv.pmaas.entity.AppointmentStatus.CANCELLED
        GROUP BY a.appointmentDate
        ORDER BY a.appointmentDate ASC
        """
    )
    List<Object[]> countByDateRange(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    /**
     * Count appointments by date AND visit type.
     * Returns Object[] { LocalDate date, String visitType, Long count }.
     */
    @Query(
        """
        SELECT a.appointmentDate, a.visitType, COUNT(a)
        FROM Appointment a
        WHERE a.appointmentDate BETWEEN :from AND :to
          AND a.status <> dev.mmiv.pmaas.entity.AppointmentStatus.CANCELLED
        GROUP BY a.appointmentDate, a.visitType
        ORDER BY a.appointmentDate ASC
        """
    )
    List<Object[]> countByDateRangeAndType(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    /**
     * Show-rate data: group by date, count scheduled vs completed.
     * Returns Object[] { LocalDate date, Long scheduledCount, Long completedCount, Long noShowCount }.
     */
    @Query(
        """
        SELECT
          a.appointmentDate,
          COUNT(a),
          SUM(CASE WHEN a.status = dev.mmiv.pmaas.entity.AppointmentStatus.COMPLETED THEN 1 ELSE 0 END),
          SUM(CASE WHEN a.status = dev.mmiv.pmaas.entity.AppointmentStatus.NO_SHOW   THEN 1 ELSE 0 END)
        FROM Appointment a
        WHERE a.appointmentDate BETWEEN :from AND :to
          AND a.status <> dev.mmiv.pmaas.entity.AppointmentStatus.CANCELLED
        GROUP BY a.appointmentDate
        ORDER BY a.appointmentDate ASC
        """
    )
    List<Object[]> showRateByDateRange(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    /**
     * Count of today's appointments by status.
     * Used by the KPI card.
     */
    @Query(
        """
        SELECT COUNT(a) FROM Appointment a
        WHERE a.appointmentDate = :today
          AND a.status NOT IN (
              dev.mmiv.pmaas.entity.AppointmentStatus.CANCELLED
          )
        """
    )
    Long countTodayNonCancelled(@Param("today") LocalDate today);

    /**
     * Upcoming appointments (today onwards, non-terminal).
     * Used by nurse queue view.
     */
    @Query(
        """
        SELECT a FROM Appointment a
        LEFT JOIN FETCH a.patient
        WHERE a.appointmentDate >= :from
          AND a.status IN (
              dev.mmiv.pmaas.entity.AppointmentStatus.PENDING,
              dev.mmiv.pmaas.entity.AppointmentStatus.CONFIRMED
          )
        ORDER BY a.appointmentDate ASC, a.appointmentTime ASC NULLS LAST
        """
    )
    Page<Appointment> findUpcoming(
        @Param("from") LocalDate from,
        Pageable pageable
    );
}
