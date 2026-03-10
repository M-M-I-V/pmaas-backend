package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.DentalVisits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DentalVisitsRepository extends JpaRepository<DentalVisits, Integer> {
}
