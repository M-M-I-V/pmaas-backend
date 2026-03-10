package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.entity.Users;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JWTService {

    /**
     * Injected from application.properties → jwt.secret → ${JWT_SECRET} env var.
     * Must be a Base64-encoded string of at least 32 bytes (256 bits) for HmacSHA256.
     */
    @Value("${jwt.secret}")
    private String secretKey;

    /**
     * Token validity in milliseconds. Default: 8 hours (28,800,000 ms).
     * Configured via jwt.expiration-ms in application.properties.
     */
    @Value("${jwt.expiration-ms:28800000}")
    private long expirationMs;

    /**
     * Generates a signed JWT for the given user.
     * Embeds the role claim so the frontend can conditionally render UI.
     * NOTE: The role in the token is for UI hints only — the server always
     * re-checks the role from the database on every secured request (via JWTFilter).
     */
    public String generateToken(Users user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());

        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(user.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .and()
                .signWith(getKey())
                .compact();
    }

    /**
     * Builds the SecretKey from the injected Base64-encoded secret.
     * Called on every token operation — the key is derived from the stable
     * environment variable, so it is consistent across restarts and instances.
     */
    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}