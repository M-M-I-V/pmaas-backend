-- ============================================================================
-- V2__add_security_columns.sql
-- Adds two security-hardening columns from Priority 3 fixes:
--
--   users.enabled        — S-14: account deactivation without deletion
--   audit_log.hash       — S-09: SHA-256 hash chain for tamper detection
--
-- Both columns use safe defaults so existing rows are unaffected and the
-- application starts normally without any data backfill required.
--
-- After this migration runs:
--   - All existing users have enabled = TRUE (remain active)
--   - All existing audit_log rows have hash = NULL (pre-chain history)
--     New entries written by AuditLogService will always have a hash.
-- ============================================================================

-- ── S-14 FIX: Account deactivation support ───────────────────────────────────
ALTER TABLE users
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN users.enabled IS
    'FALSE = account is deactivated. User cannot log in but their audit history is preserved.
     Set via PUT /api/admin/users/update/{id} with { "enabled": false }.';

-- ── S-09 FIX: SHA-256 hash chain for audit log integrity ─────────────────────
ALTER TABLE audit_log
    ADD COLUMN hash VARCHAR(64);

COMMENT ON COLUMN audit_log.hash IS
    'SHA-256 hex digest of (entity_name|record_id|action|username|timestamp|details|prev_hash).
     The first entry uses "GENESIS" as prev_hash. NULL means a pre-migration legacy record.
     Any gap, deletion, or field modification breaks the chain and is detectable.';

-- ── Make all audit_log columns NOT NULL (defence-in-depth) ───────────────────
-- These were already NOT NULL in the entity, but explicit at the DB level ensures
-- the database rejects any attempt to insert a null regardless of the ORM layer.
ALTER TABLE audit_log
    ALTER COLUMN entity_name SET NOT NULL,
    ALTER COLUMN record_id   SET NOT NULL,
    ALTER COLUMN action      SET NOT NULL,
    ALTER COLUMN username    SET NOT NULL,
    ALTER COLUMN timestamp   SET NOT NULL;
