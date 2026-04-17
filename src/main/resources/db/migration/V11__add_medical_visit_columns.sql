-- ============================================================================
-- V11__add_medical_visit_columns.sql
--
-- Adds MD-specific columns to medical_visits table to match the MedicalVisits
-- JPA entity definition. These columns were in the base visits table in V1
-- but should be in the medical_visits subclass table for proper JOINED
-- inheritance mapping.
--
-- ============================================================================

-- Add MD-owned fields (set during PENDING_MD_REVIEW by assigned MD)
ALTER TABLE medical_visits
    ADD COLUMN IF NOT EXISTS history TEXT,
    ADD COLUMN IF NOT EXISTS physical_exam_findings TEXT,
    ADD COLUMN IF NOT EXISTS diagnosis TEXT,
    ADD COLUMN IF NOT EXISTS plan TEXT,
    ADD COLUMN IF NOT EXISTS treatment TEXT,
    ADD COLUMN IF NOT EXISTS diagnostic_test_result TEXT,
    ADD COLUMN IF NOT EXISTS hama TEXT,
    ADD COLUMN IF NOT EXISTS referral_form VARCHAR(512),
    ADD COLUMN IF NOT EXISTS diagnostic_test_image VARCHAR(512),
    ADD COLUMN IF NOT EXISTS medical_chart_image VARCHAR(512);

COMMENT ON COLUMN medical_visits.history IS 'Patient medical history captured by MD';
COMMENT ON COLUMN medical_visits.physical_exam_findings IS 'Physical examination findings from MD';
COMMENT ON COLUMN medical_visits.diagnosis IS 'Medical diagnosis from MD';
COMMENT ON COLUMN medical_visits.plan IS 'Treatment plan and recommendations from MD';
COMMENT ON COLUMN medical_visits.treatment IS 'Treatment provided by MD';
COMMENT ON COLUMN medical_visits.diagnostic_test_result IS 'Result of diagnostic tests';
COMMENT ON COLUMN medical_visits.hama IS 'HAMA (Health Metrics and Assessment) data from MD';
COMMENT ON COLUMN medical_visits.referral_form IS 'Blob storage path for medical referral form';
COMMENT ON COLUMN medical_visits.diagnostic_test_image IS 'Blob storage path for diagnostic test image';
COMMENT ON COLUMN medical_visits.medical_chart_image IS 'Blob storage path for medical chart image';
