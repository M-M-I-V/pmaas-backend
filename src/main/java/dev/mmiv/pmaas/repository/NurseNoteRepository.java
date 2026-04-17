package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// NurseNoteRepository
@Repository
public interface NurseNoteRepository extends JpaRepository<NurseNote, Long> {

    /**
     * All notes for a visit in chronological order.
     * idx_nurse_notes_visit_id ensures this is an index seek, not a scan.
     */
    List<NurseNote> findByVisitIdOrderByCreatedAtAsc(Long visitId);
}

