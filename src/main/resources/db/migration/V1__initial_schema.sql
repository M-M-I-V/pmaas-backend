-- ============================================================================
-- V1__initial_schema.sql
-- Flyway baseline migration — complete schema for eClinic / pmaas
--
-- This script creates the full schema in PostgreSQL syntax.
-- It matches exactly what Hibernate 7 (Spring Boot 4) would generate from
-- the entity classes, so `spring.jpa.hibernate.ddl-auto=validate` passes.
--
-- NAMING CONVENTIONS used by Hibernate's CamelCaseToUnderscoresNamingStrategy:
--   Entity class  →  table name    (e.g. MedicalVisits → medical_visits)
--   Field name    →  column name   (e.g. chiefComplaint → chief_complaint)
--
-- INHERITANCE:
--   Visits uses InheritanceType.JOINED:
--     - `visits`        holds all shared columns
--     - `medical_visits` holds MedicalVisits-only columns, PK = FK to visits.id
--     - `dental_visits`  holds DentalVisits-only columns,  PK = FK to visits.id
--
-- HOW TO USE:
--   Fresh PostgreSQL install   → Run all migrations (V1, V2, V3, V4) in order via Flyway.
--   Existing MySQL data source → Use pgloader to migrate data, then apply V1 as
--                                 a Flyway baseline (`flyway.baseline-on-migrate=true`).
-- ============================================================================

-- ── Users ───────────────────────────────────────────────────────────────────
-- Note: `enabled` column is added in V2 after the P3 security fix (S-14).
-- It is intentionally absent here so V1 exactly matches the pre-fix schema.
CREATE TABLE users (
    id       SERIAL       PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(50)  NOT NULL    -- enum: ADMIN, MD, DMD, NURSE
);

-- ── Patients ─────────────────────────────────────────────────────────────────
CREATE TABLE patients (
    id                           SERIAL       PRIMARY KEY,
    student_number               VARCHAR(255),
    last_name                    VARCHAR(255) NOT NULL,
    first_name                   VARCHAR(255) NOT NULL,
    middle_initial               VARCHAR(255),
    status                       VARCHAR(255),
    gender                       VARCHAR(255),
    birth_date                   DATE,
    height_cm                    DOUBLE PRECISION,
    weight_kg                    DOUBLE PRECISION,
    bmi                          DOUBLE PRECISION,
    category                     VARCHAR(255),
    medical_done                 VARCHAR(255),
    dental_done                  VARCHAR(255),
    contact_number               VARCHAR(255),
    health_exam_form             VARCHAR(255),
    medical_dental_info_sheet    VARCHAR(255),
    dental_chart                 VARCHAR(255),
    special_medical_condition    VARCHAR(255),
    communicable_disease         VARCHAR(255),
    emergency_contact_name       VARCHAR(255),
    emergency_contact_relationship VARCHAR(255),
    emergency_contact_number     VARCHAR(255),
    remarks                      VARCHAR(255)
);

-- ── Visits (base table for JOINED inheritance) ───────────────────────────────
CREATE TABLE visits (
    id                     SERIAL       PRIMARY KEY,
    visit_date             DATE,
    visit_type             VARCHAR(50),         -- enum: MEDICAL, DENTAL
    chief_complaint        VARCHAR(255) NOT NULL,
    temperature            DOUBLE PRECISION,
    blood_pressure         VARCHAR(255),
    pulse_rate             INTEGER      NOT NULL DEFAULT 0,
    respiratory_rate       INTEGER      NOT NULL DEFAULT 0,
    spo2                   DOUBLE PRECISION,
    history                TEXT,
    physical_exam_findings TEXT,
    diagnosis              TEXT,
    plan                   TEXT,
    treatment              TEXT,
    diagnostic_test_result TEXT,
    diagnostic_test_image  VARCHAR(255),
    patient_id             INTEGER      NOT NULL,

    CONSTRAINT fk_visits_patient
        FOREIGN KEY (patient_id) REFERENCES patients(id)
        ON DELETE CASCADE
);

-- ── MedicalVisits (JOINED subtype) ───────────────────────────────────────────
CREATE TABLE medical_visits (
    id                  INTEGER PRIMARY KEY,    -- FK to visits.id
    hama                VARCHAR(255),
    referral_form       VARCHAR(255),
    medical_chart_image VARCHAR(255),
    nurses_notes        TEXT,

    CONSTRAINT fk_medical_visits_base
        FOREIGN KEY (id) REFERENCES visits(id)
        ON DELETE CASCADE
);

-- ── DentalVisits (JOINED subtype) ────────────────────────────────────────────
CREATE TABLE dental_visits (
    id                  INTEGER PRIMARY KEY,    -- FK to visits.id
    dental_chart_image  VARCHAR(255),
    tooth_status        VARCHAR(255),

    CONSTRAINT fk_dental_visits_base
        FOREIGN KEY (id) REFERENCES visits(id)
        ON DELETE CASCADE
);

-- ── AuditLog ─────────────────────────────────────────────────────────────────
-- Note: `hash` column is added in V2 (S-09 immutability fix).
-- It is intentionally absent here so V1 exactly matches the pre-fix schema.
CREATE TABLE audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    entity_name VARCHAR(255) NOT NULL,
    record_id   INTEGER      NOT NULL,
    action      VARCHAR(255) NOT NULL,
    username    VARCHAR(255) NOT NULL,
    timestamp   TIMESTAMP    NOT NULL,
    details     TEXT
);

-- ── Indexes ──────────────────────────────────────────────────────────────────
-- Performance: the most common query patterns for this application.

-- Login: username lookup on every authenticated request
CREATE UNIQUE INDEX idx_users_username ON users(username);

-- Patient search by student number
CREATE INDEX idx_patients_student_number ON patients(student_number);

-- Visit timeline: list visits for a patient ordered by date
CREATE INDEX idx_visits_patient_id       ON visits(patient_id);
CREATE INDEX idx_visits_visit_date       ON visits(visit_date);

-- Dashboard queries: current month aggregation
CREATE INDEX idx_visits_visit_date_month ON visits(visit_date);

-- Audit log: most common query is by entity + record
CREATE INDEX idx_audit_entity_record ON audit_log(entity_name, record_id);

-- Audit log: hash chain verification walks by id order
CREATE INDEX idx_audit_log_id_desc ON audit_log(id DESC);
