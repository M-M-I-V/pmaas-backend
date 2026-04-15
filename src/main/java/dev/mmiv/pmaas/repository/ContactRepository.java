package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;

@Repository
public interface ContactRepository
        extends JpaRepository<Contact, Long>,
        JpaSpecificationExecutor<Contact> {

    /**
     * Duplicate check used by the import service.
     *
     * A record is considered a duplicate when the same person
     * (by name and contact number) has a contact logged at the
     * same date and time. This mirrors the natural uniqueness
     * constraint of the original logbook.
     *
     * contactTime is nullable — both null values are treated as equal
     * (two entries with no time on the same date for the same person
     * are still duplicates).
     */
    @Query("""
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM Contact c
        WHERE c.contactDate = :contactDate
          AND c.name = :name
          AND c.contactNumber = :contactNumber
          AND (
               (:contactTime IS NULL AND c.contactTime IS NULL)
               OR c.contactTime = :contactTime
          )
        """)
    boolean existsDuplicate(
            @Param("contactDate")   LocalDate contactDate,
            @Param("contactTime")   LocalTime contactTime,
            @Param("name")          String name,
            @Param("contactNumber") String contactNumber
    );
}