-- ============================================================================
-- V4__oauth2_support.sql
-- OAuth 2.0 / OIDC support for password-free authentication via Google.
--
-- Changes:
--   1. users table: add oauth2 identity columns, make password nullable
--   2. refresh_tokens: new table for server-side refresh token management
--
-- After this migration:
--   - Existing users retain their data; password column remains but is unused
--   - New users created via Google OAuth will have password = NULL
--   - google_sub is the primary lookup key for OAuth users (immutable Google ID)
--   - email is the human-readable identifier and domain-validation target
-- ============================================================================

-- ── Update users table for OAuth2 ────────────────────────────────────────────

-- Make password nullable — OAuth2 users have no password
ALTER TABLE users
    ALTER COLUMN password DROP NOT NULL;

-- Google's stable user identifier (the 'sub' claim in the ID token)
-- Immutable: survives email/name changes in Google Workspace
ALTER TABLE users
    ADD COLUMN google_sub  VARCHAR(255) UNIQUE;

-- User's institutional email (the domain we validate: @mcst.edu.ph)
ALTER TABLE users
    ADD COLUMN email       VARCHAR(255) UNIQUE;

-- Display name from Google profile
ALTER TABLE users
    ADD COLUMN name        VARCHAR(255);

-- Profile picture URL from Google (for UI avatars)
ALTER TABLE users
    ADD COLUMN avatar_url  VARCHAR(2048);

COMMENT ON COLUMN users.google_sub IS
    'Google OIDC subject identifier (sub claim). Immutable. Primary OAuth2 lookup key.';
COMMENT ON COLUMN users.email IS
    'Institutional email address. Must end with @mcst.edu.ph. Domain-validated on every login.';
COMMENT ON COLUMN users.password IS
    'BCrypt password hash. NULL for OAuth2-only users. Kept for potential future use.';

-- ── Refresh tokens table ──────────────────────────────────────────────────────
-- Stores server-side refresh tokens for rotation and revocation.
-- The actual token value is a random UUID stored in the user's httpOnly cookie.
-- We store its SHA-256 hash here — if the DB is breached, raw tokens are not exposed.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,  -- SHA-256 hex of the raw UUID token
    user_id     INTEGER      NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);

COMMENT ON TABLE refresh_tokens IS
    'Server-side refresh token store. Enables token rotation and immediate revocation.
     Rotation: each use marks the token as revoked and issues a new one.
     Reuse detection: if a revoked token is presented, all tokens for that user are revoked.';

COMMENT ON COLUMN refresh_tokens.token_hash IS
    'SHA-256 hex digest of the raw token UUID. The plaintext token lives in the httpOnly cookie.
     Hashing ensures the DB alone cannot be used to impersonate users without the cookie.';

-- Indexes for refresh token lookups
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- ── Optional: seed admin email from environment ───────────────────────────────
-- If you want to auto-promote a specific email to ADMIN on first login,
-- store the target email here and the OAuth2LoginSuccessHandler will check it.
-- Alternatively, run: UPDATE users SET role = 'ADMIN' WHERE email = 'your@mcst.edu.ph';
