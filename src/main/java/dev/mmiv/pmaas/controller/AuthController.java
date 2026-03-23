package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.UserResponse;
import dev.mmiv.pmaas.entity.UserPrincipal;
import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.repository.UsersRepository;
import dev.mmiv.pmaas.service.JWTService;
import dev.mmiv.pmaas.service.RefreshTokenService;
import dev.mmiv.pmaas.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;

/**
 * Authentication endpoints.
 *
 * NOTE: There is NO login endpoint here. Authentication is entirely handled
 * by Spring Security's OAuth2 client. The flow is:
 *   GET /oauth2/authorization/google  → Spring Security initiates OAuth2 dance
 *   GET /login/oauth2/code/google     → Spring Security callback, handled by OAuth2LoginSuccessHandler
 *
 * This controller handles the post-authentication session management:
 *   GET  /api/auth/me       — returns the current user's profile
 *   POST /api/auth/refresh  — refreshes the access token using the refresh token cookie
 *   POST /api/auth/logout   — revokes all tokens and clears cookies
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JWTService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;
    private final UsersRepository usersRepository;

    // Current user

    /**
     * Returns the current user's profile.
     * Called by the frontend immediately after the OAuth2 callback redirect.
     * The access_token cookie is sent automatically by the browser.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Not authenticated. Please log in.");
        }

        Users user = principal.getUser();
        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        ));
    }

    // Token refresh

    /**
     * Issues a new access token + refresh token pair using the refresh token cookie.
     *
     * The refresh_token cookie is Path=/api/auth/refresh, so the browser ONLY sends
     * it to this specific endpoint — not on any other API call.
     *
     * Flow:
     *   1. Extract refresh token from cookie
     *   2. Validate + rotate (old token marked revoked, new one created)
     *   3. Issue new access token JWT
     *   4. Set both new cookies
     *
     * On reuse detection (stolen token replay), ALL user sessions are revoked.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Extract the refresh token from its path-restricted cookie
        String rawRefreshToken = extractRefreshTokenCookie(request);
        if (rawRefreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "No refresh token. Please log in again.");
        }

        // Validate and rotate — throws 401 on any issue (expired, revoked, reuse detected)
        Users user = refreshTokenService.validateAndRotate(rawRefreshToken);

        // Issue new tokens
        String newAccessToken  = jwtService.generateAccessToken(user);
        String newRefreshToken = refreshTokenService.createRefreshToken(user);

        // Set new cookies (replaces old ones)
        cookieUtil.setAccessTokenCookie(response, newAccessToken);
        cookieUtil.setRefreshTokenCookie(response, newRefreshToken);

        log.debug("Token refreshed for user '{}'", user.getEmail());
        return ResponseEntity.ok(Map.of("status", "refreshed"));
    }

    // Logout

    /**
     * Logs out the current user.
     * Revokes all refresh tokens in the database and clears both cookies.
     * After this call, neither the access token nor the refresh token are valid.
     *
     * Note: The access token may still technically be valid for up to 15 minutes
     * (until it expires naturally). For immediate revocation, implement a token
     * blacklist using Redis. For a 4-7 user clinic app, the 15-minute window is
     * an acceptable trade-off.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletResponse response) {

        if (principal != null) {
            refreshTokenService.revokeAllForUser(principal.getUser());
            log.info("User '{}' logged out", principal.getUser().getEmail());
        }

        cookieUtil.clearAuthCookies(response);
        return ResponseEntity.ok(Map.of("status", "logged_out"));
    }

    // Private helpers

    private String extractRefreshTokenCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}