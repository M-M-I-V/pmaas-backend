package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.VisitStatus;
import java.time.LocalDate;

/**
 * Lightweight visit summary DTO used by the visits table and the
 * per-patient visit history table in the frontend.
 *
 * ADDED: assignedToUserId
 *   Required by the frontend's "My Queue" filter. MD and DMD users filter
 *   visits where assignedToUserId matches their own user ID and status is
 *   PENDING_MD_REVIEW or PENDING_DMD_REVIEW. Without this field the filter
 *   cannot work client-side.
 *
 *   The field is nullable — null until a NURSE assigns the visit.
 *   The frontend must handle null (unassigned visits have no queue owner).
 */
public record VisitsList(
    Long id,
    String fullName,
    LocalDate birthDate,
    LocalDate visitDate,
    String visitType,
    String chiefComplaint,
    String physicalExamFindings,
    String diagnosis,
    String treatment,
    VisitStatus status,

    /**
     * User ID of the MD or DMD currently assigned to this visit.
     * Null until the NURSE assigns. Used by the frontend "My Queue" filter:
     *   visit.assignedToUserId === currentUser.id
     *     && visit.status !== "COMPLETED"
     *     && visit.status !== "CREATED_BY_NURSE"
     */
    Long assignedToUserId
) {}
