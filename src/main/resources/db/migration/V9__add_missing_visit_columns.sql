-- ============================================================================
-- V9__add_missing_visit_columns.sql
--
-- Adds missing columns to medical_visits and dental_visits tables to match
-- the JPA entity definitions.
--
-- Column naming follows Hibernate's CamelCaseToUnderscoresNamingStrategy:
--   Entity field → Database column
--
-- ============================================================================

-- ── Add missing columns to medical_visits ─────────────────────────────────────

ALTER TABLE medical_visits
    ADD COLUMN weight VARCHAR(20),
    ADD COLUMN height VARCHAR(20);

COMMENT ON COLUMN medical_visits.weight IS 'Patient weight captured by nurse';
COMMENT ON COLUMN medical_visits.height IS 'Patient height captured by nurse';


-- ── Add missing columns to dental_visits ──────────────────────────────────────

ALTER TABLE dental_visits
    ADD COLUMN dental_notes TEXT,
    ADD COLUMN treatment_provided TEXT,
    ADD COLUMN tooth_involved VARCHAR(100),
    ADD COLUMN diagnosis TEXT,
    ADD COLUMN plan TEXT,
    ADD COLUMN referral_form VARCHAR(512);

COMMENT ON COLUMN dental_visits.dental_notes IS 'Detailed notes from DMD clinical examination';
COMMENT ON COLUMN dental_visits.treatment_provided IS 'Treatment provided by DMD';
COMMENT ON COLUMN dental_visits.tooth_involved IS 'Tooth or teeth involved in diagnosis/treatment';
COMMENT ON COLUMN dental_visits.diagnosis IS 'Dental diagnosis from DMD';
COMMENT ON COLUMN dental_visits.plan IS 'Treatment plan and recommendations from DMD';
COMMENT ON COLUMN dental_visits.referral_form IS 'Blob storage path for referral form document';
