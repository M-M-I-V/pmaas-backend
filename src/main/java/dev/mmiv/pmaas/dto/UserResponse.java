package dev.mmiv.pmaas.dto;

/**
 * Safe user representation for API responses.
 *
 * Added the enabled field.
 *
 * The previous version omitted enabled, which meant the admin edit form had
 * no way to know whether an account was active or suspended when it loaded.
 * The form would always default the enabled toggle to true regardless of the
 * actual state, so editing any other field (e.g. changing a role) would
 * silently re-enable a suspended account on save.
 *
 * The Users entity contains no password. Returning
 * the entity directly from any endpoint is safe from that angle, but it still
 * exposes OAuth2 identity fields (googleSub, avatarUrl) that the frontend has
 * no legitimate use for. This DTO contains only the fields the frontend needs.
 */
public record UserResponse(
    int id,
    String username,
    String role,
    boolean enabled
) {}
