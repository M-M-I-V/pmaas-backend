package dev.mmiv.pmaas.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Utility for creating and clearing the two authentication cookies.
 * Why ResponseCookie instead of javax.servlet.http.Cookie?
 *   ResponseCookie supports the SameSite attribute, which is not available
 *   on the legacy Cookie class. SameSite=Lax is critical for CSRF protection
 *   while still allowing the OAuth2 redirect to work.
 * Cookie strategy:
 *   access_token:  httpOnly, Secure, SameSite=Lax, Path=/,              15 min
 *   refresh_token: httpOnly, Secure, SameSite=Lax, Path=/api/auth/refresh, 7 days
 * Path restriction on refresh_token:
 *   The browser only sends the refresh_token cookie to /api/auth/refresh.
 *   This limits the exposure of the refresh token — it isn't sent on every
 *   API call, only when explicitly refreshing. An attacker cannot use the
 *   refresh token to access any endpoint other than the refresh endpoint itself.
 */
@Component
public class CookieUtil {

    private static final String ACCESS_TOKEN_COOKIE  = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private static final int ACCESS_TOKEN_MAX_AGE_SECONDS  = 900;       // 15 minutes
    private static final int REFRESH_TOKEN_MAX_AGE_SECONDS = 604_800;   // 7 days

    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    // Set cookies

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
                "/api/auth/refresh",          // Path-restricted to refresh endpoint only
                REFRESH_TOKEN_MAX_AGE_SECONDS
        );
        response.addHeader("Set-Cookie", cookie.toString());
    }

    // Clear cookies (logout)

    /**
     * Clears both cookies by setting MaxAge=0.
     * The browser deletes cookies when it receives a MaxAge=0 directive.
     * Path must match the original cookie's path exactly, or the browser
     * will not delete the cookie.
     */
    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildClearCookie(ACCESS_TOKEN_COOKIE, "/").toString());
        response.addHeader("Set-Cookie", buildClearCookie(REFRESH_TOKEN_COOKIE, "/api/auth/refresh").toString());
    }

    // Private helpers

    private ResponseCookie buildCookie(String name, String value, String path, int maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)             // JS cannot read this cookie
                .secure(secureCookie)       // HTTPS only (set app.cookie.secure=false for localhost HTTP)
                .sameSite("Lax")           // CSRF protection; allows OAuth redirect
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie buildClearCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path(path)
                .maxAge(0)     // MaxAge=0 instructs the browser to delete the cookie
                .build();
    }
}