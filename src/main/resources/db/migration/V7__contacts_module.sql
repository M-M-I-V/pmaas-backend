-- ─────────────────────────────────────────────────────────────────────────────
-- V7__contacts_module.sql
--
-- Creates the contacts table and all supporting indexes.
-- Part of the Contacts Module — digitization of the "Contacts Log 2025"
-- Excel logbook for MCST Health Services Office.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE contacts (
                          id                   BIGSERIAL    PRIMARY KEY,

    -- Core logbook fields
                          contact_date         DATE         NOT NULL,
                          contact_time         TIME,
                          name                 VARCHAR(255) NOT NULL,
                          designation          VARCHAR(100),

    -- Enumerated fields — stored as VARCHAR for readability in direct SQL queries
                          visit_type           VARCHAR(20),
                          mode_of_communication VARCHAR(30),
                          respond              VARCHAR(30),

    -- Free-text fields
                          contact_number       VARCHAR(50),
                          purpose              TEXT,
                          remarks              TEXT,

    -- Foreign key to patients — nullable (walk-ins, guardians may not have a record)
                          patient_id           BIGINT
                                                            REFERENCES patients(id)
                                                                ON DELETE SET NULL,

    -- Audit timestamps
                          created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

-- Date range filtering is the most common query pattern
CREATE INDEX idx_contacts_date
    ON contacts (contact_date);

-- Name partial match filtering
CREATE INDEX idx_contacts_name
    ON contacts (lower(name));

-- Medical/Dental filter
CREATE INDEX idx_contacts_visit_type
    ON contacts (visit_type);

-- Mode of communication filter
CREATE INDEX idx_contacts_mode
    ON contacts (mode_of_communication);

-- Respond status filter
CREATE INDEX idx_contacts_respond
    ON contacts (respond);

-- Patient association
CREATE INDEX idx_contacts_patient_id
    ON contacts (patient_id);

-- Composite index used by the duplicate-check query in ContactRepository
CREATE INDEX idx_contacts_duplicate_check
    ON contacts (contact_date, name, contact_number);


-- ── Audit immutability — consistent with existing audit policy ────────────────
-- Prevents direct deletion of contacts via SQL (must go through the application).
-- Mirror of the pattern applied to other audit-sensitive tables.

REVOKE DELETE ON contacts FROM PUBLIC;

-- Allow the application role to insert and update only
-- (Replace 'clinic_app' with your actual PostgreSQL application role if set)
-- GRANT SELECT, INSERT, UPDATE ON contacts TO clinic_app;
-- GRANT USAGE, SELECT ON SEQUENCE contacts_id_seq TO clinic_app;


COMMENT ON TABLE contacts IS
    'Communication log between clinic staff and patients or guardians. '
    'Digitizes the "Contacts Log 2025" Excel workbook.';

COMMENT ON COLUMN contacts.patient_id IS
    'Optional link to a patient record. NULL for walk-ins or guardians '
    'not yet registered in the system.';

COMMENT ON COLUMN contacts.respond IS
    'Outcome of the contact attempt. '
    'Values: RESPONDED, NO_RESPONSE, LEFT_MESSAGE, CALLBACK_REQUESTED, PENDING';

COMMENT ON COLUMN contacts.visit_type IS
    'Whether the contact relates to a medical or dental concern. '
    'Values: MEDICAL, DENTAL';