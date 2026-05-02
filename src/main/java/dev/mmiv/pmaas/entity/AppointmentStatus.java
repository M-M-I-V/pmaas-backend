package dev.mmiv.pmaas.entity;

/**
 * Lifecycle status for an Appointment.
 *
 * State machine:
 *
 *   PENDING   → CONFIRMED  (staff confirms the slot)
 *   PENDING   → CANCELLED  (cancelled before confirmation)
 *   CONFIRMED → COMPLETED  (patient showed up; visit_id is set)
 *   CONFIRMED → NO_SHOW    (patient did not appear)
 *   CONFIRMED → CANCELLED  (cancelled after confirmation)
 *
 * Terminal states: COMPLETED, CANCELLED, NO_SHOW
 * Only the application layer enforces transitions — no DB constraint needed
 * because the service validates the transition before persisting.
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW;
    }
}
