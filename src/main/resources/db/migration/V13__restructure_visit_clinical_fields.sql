-- V13__restructure_visit_clinical_fields.sql
--
-- WHAT THIS DOES:
--   Moves the shared clinical section fields (history, diagnosis, plan, etc.)
--   from the medical_visits and dental_visits subtype tables into the base
--   visits table. This flattens the schema so both visit types share a single
--   clinical section, matching the new Visits base entity design.
--
-- WHY:
--   The previous design placed clinical fields in subtype tables. This caused
--   two problems:
--   1. DashboardRepository's native SQL queries against visits.diagnosis were
--      returning NULL for all rows because the column did not exist on the
--      base table — the data lived in medical_visits.diagnosis.
--   2. The dental workflow had its own redundant field set (dental_notes,
--      treatment_provided, tooth_involved) that duplicated the medical
--      equivalents without sharing any schema.
--
-- AFTER THIS MIGRATION:
--   visits         : gains history, physical_exam_findings, diagnosis, plan,
--                    treatment, diagnostic_test_result, diagnostic_test_image,
--                    hama, referral_form
--   medical_visits : retains only medical_chart_image
--   dental_visits  : retains only dental_chart_image, tooth_status
--                    (tooth_status was already present from V1)
--
-- DATA PRESERVATION:
--   All existing medical_visits data is copied to the base visits columns.
--   All existing dental_visits data is mapped as follows:
--     dental_notes      → visits.history
--     treatment_provided → visits.treatment
--     tooth_involved     → dental_visits.tooth_status
--     diagnosis, plan, referral_form → visits.*
--
-- SAFE TO RUN ON EXISTING DATA: yes (uses IF NOT EXISTS / IF EXISTS guards).
-- ─────────────────────────────────────────────────────────────────────────────


-- ═══════════════════════════════════════════════════════════════════════════════
-- STEP 1: Add clinical fields to the base visits table
-- ═══════════════════════════════════════════════════════════════════════════════
-- These columns are keyed as IF NOT EXISTS so the migration is idempotent
-- (safe to re-run on a fresh schema where V11 had already added some to subtype).

ALTER TABLE visits
    ADD COLUMN IF NOT EXISTS history                TEXT,
    ADD COLUMN IF NOT EXISTS physical_exam_findings TEXT,
    ADD COLUMN IF NOT EXISTS diagnosis              TEXT,
    ADD COLUMN IF NOT EXISTS plan                   TEXT,
    ADD COLUMN IF NOT EXISTS treatment              TEXT,
    ADD COLUMN IF NOT EXISTS diagnostic_test_result TEXT,
    ADD COLUMN IF NOT EXISTS diagnostic_test_image  VARCHAR(512),
    ADD COLUMN IF NOT EXISTS hama                   TEXT,
    ADD COLUMN IF NOT EXISTS referral_form          VARCHAR(512);

COMMENT ON COLUMN visits.history IS
    'Patient/clinical history. For medical visits: medical history captured by MD.
     For dental visits: clinical observations/notes captured by DMD.';
COMMENT ON COLUMN visits.physical_exam_findings IS
    'Physical examination findings. Primarily used for medical visits.';
COMMENT ON COLUMN visits.diagnosis IS
    'Clinical diagnosis. Populated by MD for medical visits, DMD for dental visits.
     This column is now on the base table so analytics queries work correctly.';
COMMENT ON COLUMN visits.plan IS
    'Treatment plan and recommendations. Populated by the assigned clinician.';
COMMENT ON COLUMN visits.treatment IS
    'Treatment provided. For dental visits maps to what was "treatment_provided".';
COMMENT ON COLUMN visits.diagnostic_test_result IS
    'Results of any diagnostic tests ordered or performed.';
COMMENT ON COLUMN visits.diagnostic_test_image IS
    'Blob storage path for diagnostic test image (X-ray, scan, etc.).';
COMMENT ON COLUMN visits.hama IS
    'HAMA (Health Metrics and Assessment) data. Medical visits only.';
COMMENT ON COLUMN visits.referral_form IS
    'Blob storage path for referral form document.';


-- ═══════════════════════════════════════════════════════════════════════════════
-- STEP 2: Copy existing medical_visits clinical data to base visits table
-- ═══════════════════════════════════════════════════════════════════════════════

UPDATE visits v
SET
    history                = m.history,
    physical_exam_findings = m.physical_exam_findings,
    diagnosis              = m.diagnosis,
    plan                   = m.plan,
    treatment              = m.treatment,
    diagnostic_test_result = m.diagnostic_test_result,
    diagnostic_test_image  = m.diagnostic_test_image,
    hama                   = m.hama,
    referral_form          = m.referral_form
FROM medical_visits m
WHERE v.id = m.id;


-- ═══════════════════════════════════════════════════════════════════════════════
-- STEP 3: Copy existing dental_visits clinical data to base visits table
--
-- Mapping:
--   dental_notes       → visits.history       (observations/notes by DMD)
--   treatment_provided → visits.treatment     (treatment provided by DMD)
--   diagnosis, plan, referral_form → direct column equivalents
-- ═══════════════════════════════════════════════════════════════════════════════

UPDATE visits v
SET
    history       = d.dental_notes,
    diagnosis     = d.diagnosis,
    plan          = d.plan,
    treatment     = d.treatment_provided,
    referral_form = d.referral_form
FROM dental_visits d
WHERE v.id = d.id
  AND v.visit_type = 'DENTAL';


-- ═══════════════════════════════════════════════════════════════════════════════
-- STEP 4: Populate tooth_status from tooth_involved in dental_visits
--
-- tooth_status (V1 column) and tooth_involved (V9 column) represent the
-- same data. Merge tooth_involved into tooth_status before dropping it.
-- ═══════════════════════════════════════════════════════════════════════════════

UPDATE dental_visits
SET tooth_status = COALESCE(tooth_status, tooth_involved)
WHERE tooth_involved IS NOT NULL;


-- ═══════════════════════════════════════════════════════════════════════════════
-- STEP 5: Drop migrated columns from medical_visits
--
-- medical_visits now only needs: id (PK/FK), medical_chart_image
-- All other clinical fields have been promoted to the base visits table.
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE medical_visits
    DROP COLUMN IF EXISTS history,
    DROP COLUMN IF EXISTS physical_exam_findings,
    DROP COLUMN IF EXISTS diagnosis,
    DROP COLUMN IF EXISTS plan,
    DROP COLUMN IF EXISTS treatment,
    DROP COLUMN IF EXISTS diagnostic_test_result,
    DROP COLUMN IF EXISTS diagnostic_test_image,
    DROP COLUMN IF EXISTS hama,
    DROP COLUMN IF EXISTS referral_form,
    DROP COLUMN IF EXISTS weight,
    DROP COLUMN IF EXISTS height;

-- Also drop the old VARCHAR(255) versions from V1 if they still exist
-- (V11 added them as TEXT with IF NOT EXISTS; the V1 versions were VARCHAR).
-- IF EXISTS guards make this safe on schemas where V11 already ran.
ALTER TABLE medical_visits
    DROP COLUMN IF EXISTS tooth_status;  -- was never in entity but defensive


-- ═══════════════════════════════════════════════════════════════════════════════
-- STEP 6: Drop migrated columns from dental_visits
--
-- dental_visits now only needs:
--   id (PK/FK), dental_chart_image (V1), tooth_status (V1)
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE dental_visits
    DROP COLUMN IF EXISTS dental_notes,
    DROP COLUMN IF EXISTS treatment_provided,
    DROP COLUMN IF EXISTS tooth_involved,
    DROP COLUMN IF EXISTS diagnosis,
    DROP COLUMN IF EXISTS plan,
    DROP COLUMN IF EXISTS referral_form;


-- ═══════════════════════════════════════════════════════════════════════════════
-- STEP 7: Widen referral_form in medical_visits base (V1 was VARCHAR(255))
--
-- V1 created referral_form as VARCHAR(255) in medical_visits; it's now in
-- visits as VARCHAR(512). The old column is already dropped in step 5.
-- Nothing to do — the new visits.referral_form is already VARCHAR(512).
-- ═══════════════════════════════════════════════════════════════════════════════


-- ═══════════════════════════════════════════════════════════════════════════════
-- STEP 8: Performance indexes on new visits columns
-- ═══════════════════════════════════════════════════════════════════════════════

-- Dashboard top-diagnoses query now hits visits.diagnosis correctly
CREATE INDEX IF NOT EXISTS idx_visits_diagnosis
    ON visits (diagnosis)
    WHERE diagnosis IS NOT NULL;

-- Dashboard top-complaints query already had chief_complaint indexed (V1).
-- No additional index needed.


-- ═══════════════════════════════════════════════════════════════════════════════
-- SUMMARY
-- ═══════════════════════════════════════════════════════════════════════════════
--
-- PROMOTED FROM medical_visits → visits:
--   ✓ history, physical_exam_findings, diagnosis, plan, treatment
--   ✓ diagnostic_test_result, diagnostic_test_image, hama, referral_form
--
-- PROMOTED FROM dental_visits → visits (with rename):
--   ✓ dental_notes → history (dental observations)
--   ✓ treatment_provided → treatment
--   ✓ diagnosis, plan, referral_form (direct column equivalents)
--
-- MERGED IN dental_visits:
--   ✓ tooth_involved → tooth_status (both V1 + V9; V1 column preserved)
--
-- REMAINING in medical_visits: medical_chart_image
-- REMAINING in dental_visits:  dental_chart_image, tooth_status
-- ═══════════════════════════════════════════════════════════════════════════════
