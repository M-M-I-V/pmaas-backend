package dev.mmiv.pmaas.entity;

import dev.mmiv.pmaas.exception.InvalidStateTransitionException;
import dev.mmiv.pmaas.entity.VisitType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Visit workflow status with embedded state machine.
 *
 * Each constant knows which statuses it can legally transition to
 * and which visit type that transition applies to. This keeps all
 * transition rules in one place — services call validateTransition()
 * and never need to reason about valid paths themselves.
 *
 * Medical workflow:
 *   CREATED_BY_NURSE → PENDING_MD_REVIEW → PENDING_NURSE_REVIEW → COMPLETED
 *
 * Dental workflow:
 *   CREATED_BY_NURSE → PENDING_DMD_REVIEW → COMPLETED
 */
public enum VisitStatus {

    CREATED_BY_NURSE {
        @Override
        public Set<VisitStatus> allowedNext(VisitType visitType) {
            return switch (visitType) {
                case MEDICAL -> EnumSet.of(PENDING_MD_REVIEW);
                case DENTAL  -> EnumSet.of(PENDING_DMD_REVIEW);
            };
        }
    },

    PENDING_MD_REVIEW {
        @Override
        public Set<VisitStatus> allowedNext(VisitType visitType) {
            return EnumSet.of(PENDING_NURSE_REVIEW);
        }
    },

    PENDING_DMD_REVIEW {
        @Override
        public Set<VisitStatus> allowedNext(VisitType visitType) {
            return EnumSet.of(COMPLETED);
        }
    },

    PENDING_NURSE_REVIEW {
        @Override
        public Set<VisitStatus> allowedNext(VisitType visitType) {
            // Nurse adds a note → transitions to COMPLETED.
            // After COMPLETED, nurse may still add notes (see isNoteAddAllowed).
            return EnumSet.of(COMPLETED);
        }
    },

    COMPLETED {
        @Override
        public Set<VisitStatus> allowedNext(VisitType visitType) {
            return EnumSet.noneOf(VisitStatus.class);
        }
    };

    // ── Abstract contract ─────────────────────────────────────────────────────

    public abstract Set<VisitStatus> allowedNext(VisitType visitType);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Asserts that a transition from this status to {@code next} is legal
     * for the given visit type. Throws {@link InvalidStateTransitionException}
     * if the transition is not allowed.
     *
     * Usage: currentStatus.validateTransition(targetStatus, visitType, visitId)
     */
    public void validateTransition(VisitStatus next, VisitType visitType, Long visitId) {
        if (!allowedNext(visitType).contains(next)) {
            throw new InvalidStateTransitionException(
                    visitId,
                    this,
                    next,
                    "Transition from " + this.name() + " to " + next.name() +
                            " is not allowed for " + visitType.name() + " visits."
            );
        }
    }

    /**
     * Returns true if a nurse note can be added in this status.
     * Notes are allowed during PENDING_NURSE_REVIEW (first note transitions
     * to COMPLETED) and on already-COMPLETED visits (subsequent notes).
     */
    public boolean isNoteAddAllowed() {
        return this == PENDING_NURSE_REVIEW || this == COMPLETED;
    }

    /** Returns true if prescriptions can be written in this status. */
    public boolean isPrescribeAllowed() {
        return this == PENDING_MD_REVIEW
                || this == PENDING_DMD_REVIEW
                || this == PENDING_NURSE_REVIEW
                || this == COMPLETED;
    }
}