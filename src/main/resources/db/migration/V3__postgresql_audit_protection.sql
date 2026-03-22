-- ============================================================================
-- V3__postgresql_audit_protection.sql
-- Database-level protection for the audit_log table (PostgreSQL only).
--
-- This migration implements Section 5.4 of the code review:
--   1. A trigger that RAISES EXCEPTION on any UPDATE or DELETE of audit_log rows.
--   2. REVOKE of UPDATE and DELETE privileges from the application database user.
--
-- These two layers work independently:
--   - The trigger fires even if the app user somehow retains UPDATE/DELETE (belt).
--   - The REVOKE prevents the privilege from being used in the first place (suspenders).
--   - Together they mean: not even a compromised ADMIN account in the application
--     can silently alter audit history.
--
-- PREREQUISITE:
--   Replace 'pmaas_app_user' below with the actual PostgreSQL role that the
--   application connects as (the value of DB_USERNAME in your environment).
--   Run: SELECT current_user; in psql to confirm.
--
-- NOTE: This migration runs with the Flyway migration user (which needs CREATE
-- TRIGGER and GRANT/REVOKE privileges). The application user (pmaas_app_user)
-- must be a different, lower-privilege role.
-- ============================================================================

-- ── Immutability trigger ─────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'Audit log records are immutable. Operation: %, Table: audit_log, Row ID: %',
        TG_OP,
        COALESCE(OLD.id::TEXT, 'unknown');
END;
$$;

COMMENT ON FUNCTION prevent_audit_log_modification() IS
    'Trigger function that unconditionally rejects any UPDATE or DELETE on audit_log.
     Fires BEFORE the operation so no partial data is written.
     Applied by trigger audit_log_immutable below.';

-- Attach the trigger to BOTH UPDATE and DELETE, for EACH ROW.
-- BEFORE means the exception is raised before any disk write occurs.
CREATE TRIGGER audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_modification();

COMMENT ON TRIGGER audit_log_immutable ON audit_log IS
    'Prevents any modification or deletion of audit log entries.
     Even a superuser-granted UPDATE/DELETE will be blocked at the trigger level.
     To intentionally archive or purge audit records, disable this trigger first
     via a controlled database maintenance procedure, not through the application.';

-- ── Privilege revocation from application user ────────────────────────────────
--
-- IMPORTANT: Replace 'pmaas_app_user' with the actual DB role name.
--            This is the role whose credentials are in DB_USERNAME / application.properties.
--
-- The application user needs only SELECT and INSERT on audit_log.
-- UPDATE and DELETE are never legitimate operations from the application.
--
DO $$
DECLARE
    app_user TEXT := 'pmaas';  -- ← CHANGE THIS to your actual DB username
BEGIN
    -- Revoke broad privileges first, then grant only what is needed
    EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE audit_log FROM %I', app_user);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE audit_log TO %I', app_user);

    RAISE NOTICE 'Audit log privileges configured for role: %', app_user;
    RAISE NOTICE 'Granted: SELECT, INSERT. Revoked: UPDATE, DELETE.';
END;
$$;

-- ── Sequence privilege for audit_log_id_seq ───────────────────────────────────
-- The application user needs USAGE on the sequence to generate new IDs via INSERT.
DO $$
DECLARE
    app_user TEXT := 'pmaas';  -- ← CHANGE THIS to match above
BEGIN
    EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE audit_log_id_seq TO %I', app_user);
    RAISE NOTICE 'Sequence audit_log_id_seq: USAGE, SELECT granted to %', app_user;
END;
$$;

-- ── Verify the setup (informational only, does not affect migration outcome) ──
DO $$
BEGIN
    RAISE NOTICE '=== V3 Audit Protection Summary ===';
    RAISE NOTICE 'Trigger audit_log_immutable: blocks UPDATE/DELETE at row level.';
    RAISE NOTICE 'Application role privileges: SELECT + INSERT only.';
    RAISE NOTICE 'To verify: SELECT grantee, privilege_type FROM information_schema.role_table_grants WHERE table_name = ''audit_log'';';
END;
$$;
