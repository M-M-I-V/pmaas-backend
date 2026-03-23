package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Server-side refresh token record.
 * The raw token UUID is stored in the user's httpOnly cookie.
 * Only the SHA-256 hash of that UUID is stored here, so a database breach
 * alone does not expose usable tokens.
 * Lifecycle:
 *   CREATE  — issued on successful OAuth2 login
 *   VERIFY  — on every call to /api/auth/refresh
 *   ROTATE  — old token marked revoked, new token created
 *   REVOKE  — on logout, or on reuse detection (all user tokens revoked)
 *   EXPIRE  — not cleaned up automatically; expired tokens are ignored on lookup
 *             (add a scheduled job to purge old records if needed)
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SHA-256 hex digest of the raw token UUID that is stored in the browser cookie.
     * Used for fast indexed lookups without exposing the plaintext token.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * True if this token has been used (rotated) or explicitly revoked.
     * Presenting a revoked token triggers reuse detection and revokes all
     * tokens for that user.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }

    /** Called when this token is used — marks it as rotated/consumed. */
    public void revoke() {
        this.revoked = true;
    }
}