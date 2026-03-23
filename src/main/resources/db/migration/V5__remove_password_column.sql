-- V5__remove_password_column.sql
-- Password-based authentication was eliminated in V4 (OAuth2-only via Google OIDC).
-- The password column is no longer written to by any code path and is removed
-- to keep the schema consistent with the system's actual authentication model.
--
-- If a break-glass local admin account is ever needed, add a separate
-- admin_credentials table rather than re-adding this column.
ALTER TABLE users DROP COLUMN password;