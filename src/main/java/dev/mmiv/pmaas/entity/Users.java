package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Application user entity.
 * Updated for OAuth2/OIDC authentication:
 *   - password field REMOVED (OAuth2 users have no password, handled via Google)
 *   - email is the institutional identifier (@mcst.edu.ph)
 *   - googleSub is Google's stable, immutable user ID (survives email/name changes)
 *   - name and avatarUrl come from Google's userinfo endpoint
 * S-14 FIX: enabled field controls account deactivation without deletion.
 * S-09 FIX: AuditLog entity is separate and uses @Builder + immutability.
 */
@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /**
     * Legacy login identifier. For OAuth2 users this is set to the email address.
     * Kept for compatibility with existing admin endpoints and audit logs.
     */
    @Column(unique = true, nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * Account deactivation without deletion.
     * false = user cannot log in; their audit history is preserved.
     */
    @Column(nullable = false)
    private boolean enabled = true;

    // ── OAuth2 / OIDC fields ────────────────────────────────────────────────

    /**
     * Google's stable subject identifier (the 'sub' claim in the ID token).
     * Immutable — survives email or name changes in Google Workspace.
     * Primary lookup key for OAuth2 authentication.
     */
    @Column(unique = true)
    private String googleSub;

    /**
     * Institutional email address (@mcst.edu.ph).
     * Domain-validated on every OAuth2 login before any JWT is issued.
     * Used as the JWT 'sub' claim (human-readable, auditable identifier).
     */
    @Column(unique = true)
    private String email;

    /** Display name from Google profile. Used in the UI. */
    private String name;

    /** Google profile picture URL. Used for user avatars in the UI. */
    @Column(length = 2048)
    private String avatarUrl;
}