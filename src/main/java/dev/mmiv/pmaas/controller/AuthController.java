package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.LoginRequest;
import dev.mmiv.pmaas.dto.LoginResponse;
import dev.mmiv.pmaas.service.AuditLogService;
import dev.mmiv.pmaas.service.UsersService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles authentication requests.
 * Rate limiting via Bucket4j.
 *   A per-IP token bucket allows 5 login attempts per 15-minute window.
 *   Excess requests receive 429 Too Many Requests with a Retry-After header.
 *   This prevents brute-force and credential stuffing attacks.
 * LoginRequest DTO replaces the raw Users entity as the request body.
 *   The Users entity exposed internal field names and allowed mass assignment
 *   of fields like 'id' and 'role'.
 * CORS is handled globally in WebSecurityConfiguration.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsersService usersService;
    private final AuditLogService auditLogService;

    /**
     * Per-IP token bucket map.
     * Each IP gets its own Bucket allowing 5 attempts per 15-minute window.
     * Memory note: acceptable for a clinic with a small known user base.
     * For internet-exposed deployments, replace with a Caffeine cache with TTL.
     */
    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest,
                                   HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);

        // Rate limit check
        Bucket bucket = loginBuckets.computeIfAbsent(clientIp, this::newLoginBucket);
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for login from IP: {}", clientIp);
            auditLogService.record("AUTH", 0, "RATE_LIMITED",
                    "Login rate limit exceeded from IP: " + clientIp);
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "900")
                    .body("Too many login attempts. Please wait 15 minutes before trying again.");
        }

        // Authentication
        try {
            LoginResponse response = usersService.verifyUser(loginRequest);
            auditLogService.record("AUTH", 0, "LOGIN",
                    "Successful login from IP: " + clientIp);
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException ex) {
            auditLogService.record("AUTH", 0, "LOGIN_FAILED",
                    "Failed login for username '" + loginRequest.username() +
                            "' from IP: " + clientIp);
            // Do NOT reveal whether the username exists or the password was wrong
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid credentials.");

        } catch (LockedException ex) {
            auditLogService.record("AUTH", 0, "LOGIN_FAILED",
                    "Login attempt on locked account '" + loginRequest.username() +
                            "' from IP: " + clientIp);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Account is disabled. Contact your administrator.");
        }
    }

    private Bucket newLoginBucket(String ip) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(5)
                .refillIntervally(5, Duration.ofMinutes(15))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Resolves the real client IP, accounting for reverse proxies (Render, nginx).
     * Takes only the first IP in X-Forwarded-For to prevent header spoofing.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}