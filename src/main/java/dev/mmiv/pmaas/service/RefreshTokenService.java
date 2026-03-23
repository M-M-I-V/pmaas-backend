package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.entity.RefreshToken;
import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Manages the lifecycle of server-side refresh tokens.
 * Security model:
 *   - The raw UUID token is stored in the user's httpOnly cookie (plaintext)
 *   - The SHA-256 hash of the UUID is stored in the database
 *   - This means a DB breach alone does not give the attacker usable tokens
 * Token rotation (against refresh token theft):
 *   Each use of a refresh token:
 *     1. Marks the current token as revoked
 *     2. Issues a new access token + refresh token pair
 *   If an attacker and the legitimate user both hold the same refresh token,
 *   the first one to use it wins. The second attempt triggers reuse detection.
 * Reuse detection (detects stolen tokens):
 *   If a refresh token that was already revoked is presented:
 *     - ALL refresh tokens for that user are immediately revoked
 *     - The user is effectively logged out everywhere
 *     - This should be logged and may warrant a security alert
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiry-days:7}")
    private int refreshTokenExpiryDays;

    // Create

    /**
     * Creates a new refresh token for the user.
     *
     * @return the raw UUID token (to be stored in the httpOnly cookie)
     */
    @Transactional
    public String createRefreshToken(Users user) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken; // Return plaintext for cookie storage
    }

    // Validate and rotate

    /**
     * Validates a refresh token and returns the associated user.
     * If valid, the token is revoked (rotation — caller must issue a new one).
     * If already revoked, ALL tokens for the user are revoked (reuse detection).
     *
     * @param rawToken the plaintext token from the httpOnly cookie
     * @return the Users entity the token belongs to
     * @throws ResponseStatusException (401) if token is invalid, expired, or revoked
     */
    @Transactional
    public Users validateAndRotate(String rawToken) {
        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found — possible token tampering or expiry");
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                            "Invalid or expired session. Please log in again.");
                });

        // Reuse detection
        if (refreshToken.isRevoked()) {
            Users user = refreshToken.getUser();
            log.error("SECURITY ALERT: Refresh token reuse detected for user '{}'. " +
                    "Revoking ALL sessions. Possible token theft.", user.getEmail());
            // Revoke every token for this user — forces re-authentication everywhere
            refreshTokenRepository.revokeAllUserTokens(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Security violation detected. All sessions have been invalidated. " +
                            "Please log in again.");
        }

        // Expiry check
        if (refreshToken.isExpired()) {
            log.debug("Refresh token expired for user '{}'", refreshToken.getUser().getEmail());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Session expired. Please log in again.");
        }

        // Rotate: mark old token as used
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getUser();
    }

    // Revoke all (logout)

    /** Revokes all active refresh tokens for the user. Called on explicit logout. */
    @Transactional
    public void revokeAllForUser(Users user) {
        refreshTokenRepository.revokeAllUserTokens(user);
        log.debug("Revoked all refresh tokens for user '{}'", user.getEmail());
    }

    // SHA-256 hashing

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}