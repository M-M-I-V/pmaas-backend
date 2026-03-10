package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.MedicalVisits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MedicalVisitsRepository extends JpaRepository<MedicalVisits, Integer> {
}
