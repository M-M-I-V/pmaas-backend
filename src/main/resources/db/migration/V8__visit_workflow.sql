-- ─────────────────────────────────────────────────────────────────────────────
-- V8__visit_workflow.sql
--
-- Adds multi-step visit workflow to the pmaas system.
-- Migrations V1-V7 already applied (baseline, security, audit,
-- inventory module, contacts module).
--
-- Changes:
--   1. Add audit timestamp columns to visits table (created_at, updated_at, created_by)
--   2. Add workflow columns to visits table
--   3. Drop nurses_notes TEXT from medical_visits (replaced by nurse_notes table)
--   4. Create nurse_notes table (append-only, immutable rows)
--   5. Create prescriptions table (audit trail for inventory deductions)
--   6. Add performance indexes
--   7. Backfill existing visits to COMPLETED status
-- ─────────────────────────────────────────────────────────────────────────────

-- ── 1. Add audit timestamp columns to visits ──────────────────────────────────
-- These columns were missing from V1 but are required by the Visits entity.

ALTER TABLE visits
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN created_by VARCHAR(255);

-- ── 2. Workflow columns on visits ─────────────────────────────────────────────

ALTER TABLE visits
    ADD COLUMN status                VARCHAR(50)  NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN assigned_to_user_id   BIGINT,
    ADD COLUMN assigned_by           VARCHAR(255),
    ADD COLUMN assigned_at           TIMESTAMP,
    ADD COLUMN completed_at          TIMESTAMP;

-- Check constraint ensures only valid enum values are stored.
-- This is a defence-in-depth measure — the application enum already validates,
-- but a bad migration script or direct SQL insert should be caught here too.
ALTER TABLE visits
    ADD CONSTRAINT chk_visits_status
        CHECK (status IN (
                          'CREATED_BY_NURSE',
                          'PENDING_MD_REVIEW',
                          'PENDING_DMD_REVIEW',
                          'PENDING_NURSE_REVIEW',
                          'COMPLETED'
            ));

ALTER TABLE visits
    ADD CONSTRAINT fk_visits_assigned_user
        FOREIGN KEY (assigned_to_user_id) REFERENCES users(id)
            ON DELETE SET NULL;

-- Backfill: all visits created before this migration are already completed
-- (they were single-step creations). Set a sensible completed_at from updated_at.
UPDATE visits SET completed_at = updated_at WHERE status = 'COMPLETED';


-- 3. Drop legacy nursesNotes TEXT column

ALTER TABLE medical_visits DROP COLUMN IF EXISTS nurses_notes;


-- 4. nurse_notes table — append-only, immutable

CREATE TABLE nurse_notes (
     id           BIGSERIAL     PRIMARY KEY,
     visit_id     BIGINT        NOT NULL,
     content      TEXT          NOT NULL,
     created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
     created_by   VARCHAR(255)  NOT NULL,

     CONSTRAINT fk_nurse_notes_visit
         FOREIGN KEY (visit_id) REFERENCES visits(id)
             ON DELETE CASCADE
);

-- Immutability at the database level: prevent UPDATE and DELETE on nurse_notes.
-- The application never issues these statements, but this revoke prevents
-- accidental or malicious direct SQL modification.
REVOKE UPDATE, DELETE ON nurse_notes FROM PUBLIC;

COMMENT ON TABLE nurse_notes IS
    'Append-only timestamped nurse notes for medical visits. '
        'Rows are immutable: no UPDATE or DELETE is permitted. '
        'Part of V8 visit workflow migration.';


-- 5. prescriptions table — audit trail for inventory deductions

CREATE TABLE prescriptions (
   id                  BIGSERIAL     PRIMARY KEY,
   visit_id            BIGINT        NOT NULL,
   inventory_item_id   BIGINT        NOT NULL,
   quantity            INTEGER       NOT NULL CHECK (quantity > 0),
   previous_stock      INTEGER       NOT NULL,
   new_stock           INTEGER       NOT NULL,
   reason              TEXT,
   prescribed_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
   prescribed_by       VARCHAR(255)  NOT NULL,

   CONSTRAINT fk_prescriptions_visit
       FOREIGN KEY (visit_id) REFERENCES visits(id)
           ON DELETE CASCADE,

   CONSTRAINT fk_prescriptions_inventory
       FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id)
           ON DELETE RESTRICT  -- prevent deleting an inventory item that has prescriptions
);

REVOKE DELETE ON prescriptions FROM PUBLIC;

COMMENT ON TABLE prescriptions IS
    'Immutable audit trail of inventory deductions for prescriptions. '
        'previous_stock and new_stock capture point-in-time inventory state. '
        'Part of V8 visit workflow migration.';


-- ── 6. Indexes ────────────────────────────────────────────────────────────────

CREATE INDEX idx_visits_status
    ON visits (status);

CREATE INDEX idx_visits_assigned_to_user_id
    ON visits (assigned_to_user_id);

CREATE INDEX idx_nurse_notes_visit_id
    ON nurse_notes (visit_id);

CREATE INDEX idx_prescriptions_visit_id
    ON prescriptions (visit_id);

CREATE INDEX idx_prescriptions_prescribed_at
    ON prescriptions (prescribed_at);

CREATE INDEX idx_prescriptions_item_id
    ON prescriptions (inventory_item_id);