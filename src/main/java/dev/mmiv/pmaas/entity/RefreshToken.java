package dev.mmiv.pmaas.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Server-side refresh token record.
 *
 * Changed user association from FetchType.LAZY to FetchType.EAGER.
 *
 * Root cause of the LazyInitializationException at /api/auth/refresh:
 *
 *   RefreshTokenService.validateAndRotate() is @Transactional. Inside that method,
 *   refreshToken.getUser() returns a Hibernate proxy because the association was
 *   declared LAZY. The @Transactional boundary ends when validateAndRotate()
 *   returns, closing the Hibernate session. The caller — AuthController.refreshToken()
 *   — then passes the returned Users proxy to JWTService.generateAccessToken(),
 *   which calls user.getEmail(). The proxy attempts to initialise against the
 *   database, finds no open session, and throws:
 *
 *     LazyInitializationException: Could not initialize proxy
 *       [dev.mmiv.pmaas.entity.Users#1] - no session
 *
 *   Stack trace confirmed:
 *     JWTService.generateAccessToken(JWTService.java:61)     ← user.getEmail()
 *     AuthController.refreshToken(AuthController.java:110)   ← outside @Transactional
 *
 * Why EAGER is the correct semantic here:
 *
 *   Every operation on a RefreshToken — validation, rotation, revocation, reuse
 *   detection — requires access to the associated Users record. There is no code
 *   path in RefreshTokenService that loads a RefreshToken and does not need the
 *   user. LAZY loading would only be beneficial if the association were sometimes
 *   not accessed; that is never the case here.
 *
 *   With EAGER, Hibernate issues a single JOIN query instead of a separate SELECT
 *   for the user after the fact, which is both more efficient and eliminates the
 *   session-scope dependency entirely.
 *
 * Alternative that was rejected:
 *   Calling Hibernate.initialize(user) or accessing user.getId() inside the
 *   @Transactional boundary to force proxy resolution before the session closes.
 *   This is fragile — it relies on a side-effect pattern that future maintainers
 *   are unlikely to recognise, and it would break again if anyone refactors
 *   validateAndRotate() without understanding the constraint.
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
     * SHA-256 hex digest of the raw token UUID stored in the browser cookie.
     * Used for fast indexed lookups without exposing the plaintext token.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * CRITICAL FIX: was FetchType.LAZY — caused LazyInitializationException.
     *
     * Changed to FetchType.EAGER so the Users record is loaded in the same
     * query as the RefreshToken. Hibernate now issues:
     *
     *   SELECT rt.*, u.*
     *   FROM refresh_tokens rt
     *   JOIN users u ON rt.user_id = u.id
     *   WHERE rt.token_hash = ?
     *
     * instead of two separate queries with a session-scope dependency between them.
     */
    @ManyToOne(fetch = FetchType.EAGER)
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