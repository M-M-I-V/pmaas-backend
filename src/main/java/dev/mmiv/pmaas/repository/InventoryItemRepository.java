package dev.mmiv.pmaas.repository;

import dev.mmiv.pmaas.entity.InventoryItem;
import dev.mmiv.pmaas.entity.ItemCategory;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryItemRepository
    extends JpaRepository<InventoryItem, Long>
{
    /**
     * Case-insensitive partial-match search across itemName, brandName, and
     * description. Passing null for q returns all records (no filter applied).
     * Passing null for category returns all categories.
     *
     * PostgreSQL note: LOWER(x) LIKE LOWER('%q%') is equivalent to ILIKE.
     * The functional index on lower(item_name) in the migration accelerates
     * the most common single-field search path.
     */
    @Query(
            """
            SELECT i FROM InventoryItem i
            WHERE (:q = ''
                   OR LOWER(i.itemName)    LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(i.brandName)   LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(i.description) LIKE LOWER(CONCAT('%', :q, '%')))
            AND (:category IS NULL OR i.category = :category)
            """
    )
    Page<InventoryItem> search(
            @Param("q") String q,
            @Param("category") ItemCategory category,
            Pageable pageable
    );

    /** Used during Excel import to detect duplicate rows. */
    boolean existsByItemNameIgnoreCaseAndDateReceivedAndCategory(
        String itemName,
        LocalDate dateReceived,
        ItemCategory category
    );

    /** Used by the export service to stream by category efficiently. */
    List<InventoryItem> findAllByCategoryOrderByItemNameAsc(
        ItemCategory category
    );
}
