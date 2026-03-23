package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for POST /api/admin/users/add — pre-provisioning a new user.
 *
 * WHY NOT @RequestBody Users:
 *   The Users entity contains OAuth2-managed fields: googleSub, avatarUrl, name.
 *   These fields are set exclusively by the OAuth2LoginSuccessHandler during the
 *   user's first login. If an admin could POST a Users entity directly, they could
 *   set googleSub to an arbitrary value, corrupting the OAuth2 identity lookup and
 *   potentially associating a new email with someone else's Google account.
 *
 *   This DTO exposes only the three fields the admin legitimately controls:
 *     email    — the institutional address the user will authenticate with
 *     role     — the access level to grant
 *     enabled  — whether the account is active (default true)
 *
 *   The username field is set to email by UsersService (OAuth2 convention).
 *   All other Users fields are left null and populated by OAuth2LoginSuccessHandler
 *   on the user's first login.
 */
public record AdminCreateUserRequest(

        /**
         * Institutional email address. Must end with @mcst.edu.ph.
         * This becomes the user's primary identifier and OAuth2 lookup key.
         */
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Pattern(
                regexp = "^[^@]+@mcst\\.edu\\.ph$",
                message = "Only @mcst.edu.ph email addresses are permitted"
        )
        String email,

        /**
         * Application role. Must be one of: ADMIN, MD, DMD, NURSE.
         * The Role enum is validated by Spring's binding — an unknown value returns 400.
         */
        @NotNull(message = "Role is required")
        Role role,

        /**
         * Whether the account is active. Defaults to true if not provided.
         * Set to false to pre-create a disabled account (e.g. for future onboarding).
         */
        Boolean enabled

) {
    /** Returns true unless explicitly set to false. */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}