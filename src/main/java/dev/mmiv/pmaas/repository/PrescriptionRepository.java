package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    /**
     * All prescriptions for a visit — for audit and response building.
     */
    List<Prescription> findByVisitIdOrderByPrescribedAtAsc(Long visitId);
}
