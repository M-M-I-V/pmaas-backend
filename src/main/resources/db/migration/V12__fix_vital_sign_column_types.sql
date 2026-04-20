-- V12__fix_vital_sign_column_types.sql
--
-- Corrects the column types for vital sign fields in the visits table to
-- match the Visits JPA entity, which stores all vitals as VARCHAR strings.
-- V1 created these as numeric types (INTEGER, DOUBLE PRECISION), which causes
-- Hibernate schema validation to fail on startup with:
--   "wrong column type encountered in column [pulse_rate] in table [visits];
--    found [int4 (Types#INTEGER)], but expecting [varchar(20) (Types#VARCHAR)]"
--
-- All four affected columns are altered atomically.
-- Existing numeric data is cast to text — e.g. 72 becomes '72', 36.5 becomes '36.5'.
-- NULL values remain NULL.
-- The DEFAULT 0 on pulse_rate and respiratory_rate is dropped because the entity
-- treats absent vitals as null, not zero.

ALTER TABLE visits
ALTER COLUMN temperature      TYPE VARCHAR(20) USING temperature::TEXT,
    ALTER COLUMN pulse_rate       TYPE VARCHAR(20) USING NULLIF(pulse_rate::TEXT, '0'),
    ALTER COLUMN respiratory_rate TYPE VARCHAR(20) USING NULLIF(respiratory_rate::TEXT, '0'),
    ALTER COLUMN spo2             TYPE VARCHAR(20) USING spo2::TEXT;

ALTER TABLE visits
    ALTER COLUMN pulse_rate       DROP DEFAULT,
ALTER COLUMN respiratory_rate DROP DEFAULT;