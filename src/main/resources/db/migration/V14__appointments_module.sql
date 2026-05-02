-- V14__appointments_module.sql
--
-- Creates the appointments table for the Appointments module.
--
-- DESIGN DECISIONS:
--
--   1. Separate table from contacts.
--      The contacts table is a communication log (phone calls, SMS follow-ups).
--      Appointments are scheduled clinical encounters with a specific date, time,
--      and visit type. Merging them would corrupt the semantics of both modules
--      and break the show-rate analytics that rely on contacts being communication
--      records.
--
--   2. patient_id is nullable.
--      Walk-in or unregistered patients may request appointments before they have
--      a patient record. The appointment can be linked to a patient later.
--      When patient_id is set, full_name and year_section are still stored
--      directly on the appointment row so the receptionist's original form data
--      is preserved (the patient record may be updated independently).
--
--   3. year_section is stored directly on the appointment.
--      It is student-specific metadata that may differ across terms, and not all
--      patient categories (Faculty, Staff) have a year/section. Denormalising it
--      here matches exactly how the paper form is filled out.
--
--   4. status enum controls the appointment lifecycle:
--      PENDING   → appointment has been recorded, not yet confirmed
--      CONFIRMED → clinic confirmed the appointment (staff action)
--      COMPLETED → patient showed up and had a visit
--      CANCELLED → appointment was cancelled before the date
--      NO_SHOW   → patient did not appear on the appointment date
--
--   5. visit_id is nullable — linked after the patient actually visits.
--      When a COMPLETED appointment is linked to a visit, the dashboard can
--      compute show-rate from this table instead of the contacts table.
--
--   6. Dashboard compatibility:
--      The DashboardRepository's fetchDailyAppointments() and fetchShowRateDaily()
--      queries should be migrated to use this table. A separate migration or
--      application-layer change handles that switch; this migration only creates
--      the table.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE appointments (
    id                  BIGSERIAL       PRIMARY KEY,

    -- Patient association (nullable for unregistered walk-ins)
    patient_id          BIGINT
                            REFERENCES patients(id)
                            ON DELETE SET NULL,

    -- Appointment details (captured from paper form)
    full_name           VARCHAR(255)    NOT NULL,
    year_section        VARCHAR(100),                   -- e.g. "BSIT 3-A", "N/A" for non-students
    contact_number      VARCHAR(50),
    visit_type          VARCHAR(20)     NOT NULL
                            CHECK (visit_type IN ('MEDICAL', 'DENTAL')),
    chief_complaint     TEXT,
    appointment_date    DATE            NOT NULL,
    appointment_time    TIME,

    -- Lifecycle status
    status              VARCHAR(20)     NOT NULL        DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),

    -- Link to the actual visit record when the patient shows up
    visit_id            BIGINT
                            REFERENCES visits(id)
                            ON DELETE SET NULL,

    -- Notes field for any additional information (e.g. rescheduling reason)
    notes               TEXT,

    -- Audit
    created_by          VARCHAR(255)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL        DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL        DEFAULT NOW()
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

-- Primary query pattern: appointments by date (calendar view, dashboard)
CREATE INDEX idx_appointments_date
    ON appointments (appointment_date);

-- Queue view: filter by status
CREATE INDEX idx_appointments_status
    ON appointments (status);

-- Patient appointment history
CREATE INDEX idx_appointments_patient_id
    ON appointments (patient_id);

-- Visit linkage lookup
CREATE INDEX idx_appointments_visit_id
    ON appointments (visit_id)
    WHERE visit_id IS NOT NULL;

-- Dashboard query: appointments per day by visit type
CREATE INDEX idx_appointments_date_type
    ON appointments (appointment_date, visit_type);

-- ── Immutability: prevent accidental deletion via direct SQL ──────────────────
-- Appointments must be CANCELLED through the application, not deleted.
-- The trigger mirrors the pattern applied to nurse_notes and prescriptions.
REVOKE DELETE ON appointments FROM PUBLIC;

-- ── Comments ──────────────────────────────────────────────────────────────────
COMMENT ON TABLE appointments IS
    'Scheduled clinical appointments. Separate from the contacts (communication log) table.
     Drives the appointments dashboard chart and show-rate analytics.';

COMMENT ON COLUMN appointments.patient_id IS
    'Optional link to a patient record. NULL for unregistered walk-ins or patients
     not yet in the system. The appointment is still fully usable without it.';

COMMENT ON COLUMN appointments.year_section IS
    'Student year and section (e.g. "BSIT 3-A"). Denormalised from the paper form.
     NULL or "N/A" for non-student appointments (Faculty, Staff).';

COMMENT ON COLUMN appointments.visit_type IS
    'Whether this is a medical or dental appointment. DENTAL is primarily MWF
     (dentist availability) but MEDICAL is included for future scheduling needs.';

COMMENT ON COLUMN appointments.status IS
    'Lifecycle: PENDING → CONFIRMED → COMPLETED (with visit_id set)
                                    → NO_SHOW
               PENDING → CANCELLED
               CONFIRMED → CANCELLED';

COMMENT ON COLUMN appointments.visit_id IS
    'Set when the patient shows up and a visit record is created.
     Enables show-rate computation: appointments with a visit_id = showed up.';
