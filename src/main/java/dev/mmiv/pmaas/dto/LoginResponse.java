package dev.mmiv.pmaas.dto;

/**
 * Response body for POST /api/auth/login.
 * Returns only the fields the frontend needs.
 * The password hash is never included in any response DTO.
 *
 * @param token     Signed JWT Bearer token.
 * @param username  The authenticated user's username (for display purposes).
 * @param role      The user's role name (ADMIN, MD, DMD, NURSE) for frontend UI gating.
 *                  NOTE: The role is re-validated server-side on every secured request;
 *                  the frontend role claim is for UI rendering only, not for authorization.
 * @param expiresAt Unix epoch milliseconds when the token expires.
 */
public record LoginResponse(
        String token,
        String username,
        String role,
        long expiresAt
) {}
