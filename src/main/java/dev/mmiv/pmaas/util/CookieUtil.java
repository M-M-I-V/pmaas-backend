package dev.mmiv.pmaas.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Utility for creating and clearing the two authentication cookies.
 *
 * ─── PRODUCTION BUG FIX: SameSite is now configurable ────────────────────────
 *
 * Root cause of the production login failure on Render + Vercel:
 *
 *   The previous implementation hardcoded SameSite=Lax. This works for local
 *   development because the frontend (localhost:3000) and backend (localhost:8080)
 *   share the same registrable domain (localhost), making them "same-site" in
 *   the browser's classification — port differences are ignored for SameSite.
 *
 *   In production, the frontend is at pmaas-three.vercel.app (eTLD+1: vercel.app)
 *   and the backend is at pmaas-backend-zq5v.onrender.com (eTLD+1: onrender.com).
 *   These are CROSS-SITE. SameSite=Lax cookies set by the backend are NOT sent
 *   by the browser on cross-site scripted requests (fetch, XHR). So:
 *
 *     1. OAuth2 login succeeds → backend sets access_token + refresh_token
 *     2. Browser redirects to vercel.app/auth/callback (top-level navigation,
 *        cookies ARE received because this is a navigation, not a scripted request)
 *     3. Callback page calls fetch('onrender.com/api/auth/me', credentials: 'include')
 *     4. Browser classifies this as a cross-site scripted request
 *     5. Browser does NOT attach the SameSite=Lax cookie from onrender.com
 *     6. JWTFilter receives no token → JwtAuthEntryPoint returns 401
 *     7. 401 interceptor calls /api/auth/refresh → also no refresh_token → 401
 *     8. Interceptor redirects to / with no error message
 *
 *   Render logs confirmed the pattern:
 *     "Successful OAuth2 login" followed immediately by
 *     "401 UNAUTHORIZED No refresh token. Please log in again."
 *   with no /api/auth/me entry (JwtAuthEntryPoint bypasses GlobalExceptionHandler).
 *
 * Fix:
 *   SameSite is read from app.cookie.same-site (default: Lax).
 *   Set app.cookie.same-site=None in the Render environment to enable
 *   cross-site cookie sending for the production cross-domain deployment.
 *
 * SameSite=None requires Secure=true:
 *   Browsers reject SameSite=None cookies without the Secure attribute.
 *   Production: COOKIE_SECURE=true is already set on Render → OK.
 *   Local dev:  application-local.properties sets app.cookie.secure=false,
 *               which is incompatible with SameSite=None. Do NOT set
 *               app.cookie.same-site=None in application-local.properties.
 *               Local dev uses SameSite=Lax (same-site because both sides
 *               are on localhost).
 *
 * CSRF implications of SameSite=None:
 *   SameSite=Lax blocks cross-site POST requests at the browser level,
 *   providing one layer of CSRF protection. SameSite=None removes that layer.
 *   The replacement protection is already in place:
 *     - CORS with explicit allowedOrigins (WebSecurityConfiguration.java)
 *     - allowCredentials(true) combined with explicit origins means only
 *       requests from the allowlisted Vercel origin can carry credentials
 *     - An attacker origin cannot make credentialed cross-origin requests
 *       even with SameSite=None — CORS blocks the preflight/response
 *
 *   SameSite=None + CORS explicit allowlist is the standard architecture
 *   for cross-domain frontend/backend API deployments.
 *
 * Environment variable to add on Render:
 *   COOKIE_SAME_SITE=None
 *
 * application.properties entry (already added in the updated properties file):
 *   app.cookie.same-site=${COOKIE_SAME_SITE:Lax}
 */
@Component
public class CookieUtil {

    private static final String ACCESS_TOKEN_COOKIE  = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private static final int ACCESS_TOKEN_MAX_AGE_SECONDS  = 900;       // 15 minutes
    private static final int REFRESH_TOKEN_MAX_AGE_SECONDS = 604_800;   // 7 days

    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    /**
     * SameSite attribute for auth cookies.
     *
     * Local dev default: "Lax"
     *   Works because localhost:3000 and localhost:8080 are same-site.
     *
     * Production (Render env var COOKIE_SAME_SITE=None): "None"
     *   Required because vercel.app and onrender.com are cross-site.
     *   MUST be paired with Secure=true (set via COOKIE_SECURE=true on Render).
     */
    @Value("${server.servlet.session.cookie.same-site:Lax}")
    private String cookieSameSite;

    // ── Set cookies ───────────────────────────────────────────────────────────

    public void setAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = buildCookie(
                ACCESS_TOKEN_COOKIE,
                token,
                "/",
                ACCESS_TOKEN_MAX_AGE_SECONDS
        );
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = buildCookie(
                REFRESH_TOKEN_COOKIE,
                token,
                "/api/auth/refresh",    // Path-restricted to refresh endpoint only
                REFRESH_TOKEN_MAX_AGE_SECONDS
        );
        response.addHeader("Set-Cookie", cookie.toString());
    }

    // ── Clear cookies (logout) ────────────────────────────────────────────────

    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildClearCookie(ACCESS_TOKEN_COOKIE, "/").toString());
        response.addHeader("Set-Cookie", buildClearCookie(REFRESH_TOKEN_COOKIE, "/api/auth/refresh").toString());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ResponseCookie buildCookie(String name, String value, String path, int maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)   // FIX: was hardcoded "Lax"
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie buildClearCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)   // FIX: was hardcoded "Lax"
                .path(path)
                .maxAge(0)
                .build();
    }
}