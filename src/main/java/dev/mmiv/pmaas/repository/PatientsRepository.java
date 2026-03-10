package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.Patients;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientsRepository extends JpaRepository<Patients, Integer>, JpaSpecificationExecutor<Patients> {
    Optional<Patients> findByStudentNumber(String studentNumber);
}
