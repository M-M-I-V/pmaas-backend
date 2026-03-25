package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.entity.Users;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Handles JWT creation, parsing, and validation for OAuth2-based authentication.
 * Secret injected from JWT_SECRET env var — never randomly generated.
 * OAuth2 update:
 *   - Subject is now the user's email (not a username)
 *   - Added 'gid' claim (Google stable sub)
 *   - Added 'jti' claim (JWT ID for replay detection)
 *   - Added 'iss' claim (our issuer)
 *   - Reads token from httpOnly cookie, not Authorization header
 *   - Access token expiry: 15 minutes (was 8 hours — reduced for security)
 */
@Service
public class JWTService {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiry-ms:900000}") // Default: 15 minutes
    private long accessTokenExpiryMs;

    @Value("${app.issuer:pmaas}")
    private String issuer;

    // ── Token Generation ──────────────────────────────────────────────────────

    /**
     * Generates a signed access token JWT for an authenticated user.
     * Claims:
     *   sub  — user's institutional email (primary, auditable identifier)
     *   gid  — Google's stable subject ID (survives email changes)
     *   name — display name (for UI)
     *   role — application role (UI hint only — server re-validates from DB)
     *   jti  — UUID, unique per token (enables replay detection / token blacklist)
     *   iss  — our application issuer
     *   iat  — issued-at timestamp
     *   exp  — expiration: now + accessTokenExpiryMs
     */
    public String generateAccessToken(Users user) {
        return Jwts.builder()
            .claims()
            .subject(user.getEmail())
            .add("gid", user.getGoogleSub())
            .add("name", user.getName())
            .add("role", user.getRole().name())
            .add("jti", UUID.randomUUID().toString())
            .add("iss", issuer)
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(
                new Date(System.currentTimeMillis() + accessTokenExpiryMs)
            )
            .and()
            .signWith(getKey())
            .compact();
    }

    // ── Token Extraction from Request ─────────────────────────────────────────

    /**
     * Extracts the access token from the request's httpOnly cookie.
     * Falls back to the Authorization: Bearer header for backward compatibility
     * with any API clients that cannot use cookies.
     *
     * @return the raw JWT string, or empty if not present
     */
    public Optional<String> extractTokenFromRequest(
        HttpServletRequest request
    ) {
        // Primary: httpOnly cookie
        if (request.getCookies() != null) {
            Optional<String> cookieToken = Arrays.stream(request.getCookies())
                .filter(c -> ACCESS_TOKEN_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();

            // Only return early if we ACTUALLY found the token
            if (cookieToken.isPresent()) {
                return cookieToken;
            }
        }

        // Fallback: Authorization: Bearer header
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }

        return Optional.empty();
    }

    // Claim Extraction

    /** Extracts the email (subject) from a token. */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Extracts the jti (JWT ID) — used for token replay detection. */
    public String extractJti(String token) {
        return extractClaim(token, claims -> claims.get("jti", String.class));
    }

    /** Extracts the role claim — for bootstrapping SecurityContext before DB hit. */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // Token Validation

    /**
     * Validates the token against the user's email.
     * JJWT throws exceptions for signature tampering and expiration —
     * the caller (JWTFilter) must catch and handle those.
     */
    public boolean isTokenValid(String token, String email) {
        return extractEmail(token).equals(email) && !isTokenExpired(token);
    }

    // Private helpers

    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(
            Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
        );
    }
}
