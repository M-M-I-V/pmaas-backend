-- ============================================================================
-- V10__fix_id_column_types.sql (CORRECTED)
--
-- GOAL: Convert 4-byte INTEGER/SERIAL id columns to 8-byte BIGINT/BIGSERIAL
-- to match JPA entity definitions which use `Long id`.
--
-- This resolves schema validation errors like:
--   "wrong column type encountered in column [id] in table [dental_visits];
--    found [int4 (Types#INTEGER)], but expecting [bigint (Types#BIGINT)]"
--
-- CRITICAL FIX: Drop DEFAULT clauses BEFORE dropping sequences!
-- ============================================================================


-- ============================================================================
-- PHASE 0: DROP DEFAULT clauses on SERIAL columns (BEFORE dropping sequences)
-- ============================================================================

ALTER TABLE users
    ALTER COLUMN id DROP DEFAULT;

ALTER TABLE patients
    ALTER COLUMN id DROP DEFAULT;

ALTER TABLE visits
    ALTER COLUMN id DROP DEFAULT;


-- ============================================================================
-- PHASE 1: Drop all foreign key constraints that we'll need to recreate
-- ============================================================================

-- Foreign keys referencing users.id (which we'll be modifying)
ALTER TABLE refresh_tokens
    DROP CONSTRAINT IF EXISTS fk_refresh_tokens_user;

ALTER TABLE visits
    DROP CONSTRAINT IF EXISTS fk_visits_assigned_user;

-- Foreign keys referencing patients.id (which we'll be modifying)
ALTER TABLE visits
    DROP CONSTRAINT IF EXISTS fk_visits_patient;

ALTER TABLE contacts
    DROP CONSTRAINT IF EXISTS fk_contacts_patient;

-- Foreign keys referencing visits.id (which we'll be modifying)
ALTER TABLE medical_visits
    DROP CONSTRAINT IF EXISTS fk_medical_visits_base;

ALTER TABLE dental_visits
    DROP CONSTRAINT IF EXISTS fk_dental_visits_base;

ALTER TABLE nurse_notes
    DROP CONSTRAINT IF EXISTS fk_nurse_notes_visit;

ALTER TABLE prescriptions
    DROP CONSTRAINT IF EXISTS fk_prescriptions_visit;

-- Foreign keys referencing inventory_items.id (which might be affected)
ALTER TABLE prescriptions
    DROP CONSTRAINT IF EXISTS fk_prescriptions_inventory;

-- ============================================================================
-- PHASE 2: Drop old sequences (now safe because DEFAULTs are removed)
-- ============================================================================

DROP SEQUENCE IF EXISTS users_id_seq CASCADE;
DROP SEQUENCE IF EXISTS patients_id_seq CASCADE;
DROP SEQUENCE IF EXISTS visits_id_seq CASCADE;


-- ============================================================================
-- PHASE 3: Alter column types from INTEGER to BIGINT
-- ============================================================================

-- ── Primary key columns ────────────────────────────────────────────────────
ALTER TABLE users
    ALTER COLUMN id TYPE BIGINT;

ALTER TABLE patients
    ALTER COLUMN id TYPE BIGINT;

ALTER TABLE visits
    ALTER COLUMN id TYPE BIGINT;

-- ── Foreign key columns ────────────────────────────────────────────────────
ALTER TABLE visits
    ALTER COLUMN patient_id TYPE BIGINT;

ALTER TABLE refresh_tokens
    ALTER COLUMN user_id TYPE BIGINT;

-- ── Inherited/joined table primary key columns ─────────────────────────────
ALTER TABLE medical_visits
    ALTER COLUMN id TYPE BIGINT;

ALTER TABLE dental_visits
    ALTER COLUMN id TYPE BIGINT;

-- ── Audit columns ─────────────────────────────────────────────────────────
ALTER TABLE audit_log
    ALTER COLUMN record_id TYPE BIGINT;


-- ============================================================================
-- PHASE 4: Create new BIGINT sequences
-- ============================================================================

CREATE SEQUENCE users_id_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    OWNED BY users.id;

CREATE SEQUENCE patients_id_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    OWNED BY patients.id;

CREATE SEQUENCE visits_id_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
    OWNED BY visits.id;


-- ============================================================================
-- PHASE 5: Set new DEFAULT clauses (now that sequences exist)
-- ============================================================================

ALTER TABLE users
    ALTER COLUMN id SET DEFAULT nextval('users_id_seq');

ALTER TABLE patients
    ALTER COLUMN id SET DEFAULT nextval('patients_id_seq');

ALTER TABLE visits
    ALTER COLUMN id SET DEFAULT nextval('visits_id_seq');


-- ============================================================================
-- PHASE 6: Recreate all foreign key constraints
-- ============================================================================

-- ── visits.patient_id → patients.id ───────────────────────────────────────
ALTER TABLE visits
    ADD CONSTRAINT fk_visits_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id)
            ON DELETE CASCADE;

-- ── visits.assigned_to_user_id → users.id ────────────────────────────────
ALTER TABLE visits
    ADD CONSTRAINT fk_visits_assigned_user
        FOREIGN KEY (assigned_to_user_id) REFERENCES users(id)
            ON DELETE SET NULL;

-- ── medical_visits.id → visits.id ─────────────────────────────────────────
ALTER TABLE medical_visits
    ADD CONSTRAINT fk_medical_visits_base
        FOREIGN KEY (id) REFERENCES visits(id)
            ON DELETE CASCADE;

-- ── dental_visits.id → visits.id ──────────────────────────────────────────
ALTER TABLE dental_visits
    ADD CONSTRAINT fk_dental_visits_base
        FOREIGN KEY (id) REFERENCES visits(id)
            ON DELETE CASCADE;

-- ── refresh_tokens.user_id → users.id ─────────────────────────────────────
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id)
            ON DELETE CASCADE;

-- ── contacts.patient_id → patients.id ─────────────────────────────────────
-- (Note: This FK was missing; adding it now to ensure referential integrity)
ALTER TABLE contacts
    ADD CONSTRAINT fk_contacts_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id)
            ON DELETE SET NULL;

-- ── nurse_notes.visit_id → visits.id ──────────────────────────────────────
-- (Note: This FK was missing; adding it now to ensure referential integrity)
ALTER TABLE nurse_notes
    ADD CONSTRAINT fk_nurse_notes_visit
        FOREIGN KEY (visit_id) REFERENCES visits(id)
            ON DELETE CASCADE;

-- ── prescriptions.visit_id → visits.id ────────────────────────────────────
-- (Note: This FK was missing; adding it now to ensure referential integrity)
ALTER TABLE prescriptions
    ADD CONSTRAINT fk_prescriptions_visit
        FOREIGN KEY (visit_id) REFERENCES visits(id)
            ON DELETE CASCADE;

-- ── prescriptions.inventory_item_id → inventory_items.id ──────────────────
-- (Note: This FK was missing; adding it now to ensure referential integrity)
ALTER TABLE prescriptions
    ADD CONSTRAINT fk_prescriptions_inventory
        FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id)
            ON DELETE RESTRICT;


-- ============================================================================
-- SUMMARY OF CHANGES
-- ============================================================================
--
-- CONVERTED FROM INTEGER/SERIAL TO BIGINT/BIGSERIAL:
--   ✓ users.id:                  SERIAL → BIGSERIAL
--   ✓ patients.id:               SERIAL → BIGSERIAL
--   ✓ visits.id:                 SERIAL → BIGSERIAL
--   ✓ visits.patient_id:         INTEGER → BIGINT
--   ✓ refresh_tokens.user_id:    INTEGER → BIGINT
--   ✓ medical_visits.id:         INTEGER → BIGINT
--   ✓ dental_visits.id:          INTEGER → BIGINT
--   ✓ audit_log.record_id:       INTEGER → BIGINT
--
-- RESULT: Schema now matches JPA entity definitions (all IDs are `Long`)
-- ============================================================================
