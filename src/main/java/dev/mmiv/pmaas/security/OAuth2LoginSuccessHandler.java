package dev.mmiv.pmaas.security;

import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.repository.UsersRepository;
import dev.mmiv.pmaas.service.JWTService;
import dev.mmiv.pmaas.service.RefreshTokenService;
import dev.mmiv.pmaas.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Called by Spring Security after Google OIDC authentication succeeds.
 *
 * ROOT CAUSE FIX — session invalidation after JWT cookie issuance:
 *
 *   After a successful OAuth2 login, Spring Security stores the OidcUser
 *   authentication in the HTTP session (JSESSIONID). This session is set by
 *   SecurityContextHolderFilter, which runs at the START of every subsequent
 *   request — before JWTFilter.
 *
 *   The broken sequence (before this fix):
 *     1. Browser calls GET /api/auth/me with Cookie: access_token=...; JSESSIONID=...
 *     2. SecurityContextHolderFilter finds JSESSIONID, restores session,
 *        sets SecurityContext to OidcUser authentication
 *     3. JWTFilter sees authentication != null, bails out early (skips JWT processing)
 *     4. AuthController.getCurrentUser gets @AuthenticationPrincipal UserPrincipal = null
 *        (the principal is OidcUser, not UserPrincipal)
 *     5. Controller throws 401 UNAUTHORIZED
 *
 *   The fix — added at the end of onAuthenticationSuccess(), before sendRedirect():
 *     - Invalidate the HTTP session: the OAuth2 session is no longer needed
 *       after JWT cookies are issued. Invalidating it prevents Spring from
 *       restoring OidcUser authentication on subsequent API requests.
 *     - Clear the SecurityContext: prevents SecurityContextHolderFilter from
 *       saving the OidcUser authentication to a new session in its finally block.
 *
 *   After the fix:
 *     1. Browser calls GET /api/auth/me with Cookie: access_token=...; JSESSIONID=...
 *     2. SecurityContextHolderFilter finds JSESSIONID but the session is gone
 *        (invalidated) — SecurityContext remains empty
 *     3. JWTFilter finds access_token cookie, validates it, looks up user by email,
 *        sets SecurityContext to UserPrincipal authentication
 *     4. AuthController.getCurrentUser gets @AuthenticationPrincipal UserPrincipal
 *        correctly populated
 *     5. Controller returns 200 OK with user data
 *
 *   The JWT cookies (access_token, refresh_token) are written to the response
 *   headers BEFORE the session is invalidated, so invalidation does not affect
 *   them. Cookie headers are immutable once added to the response.
 *
 *   All other logic is unchanged from the previous version.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String ALLOWED_DOMAIN = "@mcst.edu.ph";

    private final UsersRepository usersRepository;
    private final JWTService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull Authentication authentication
    ) throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            log.error(
                "OAuth2 login failed: Principal is missing or not an OidcUser"
            );
            response.sendRedirect(frontendUrl + "/?error=invalid_principal");
            return;
        }

        // Step 1: Domain restriction
        String email = oidcUser.getEmail();
        if (email == null || !email.toLowerCase().endsWith(ALLOWED_DOMAIN)) {
            log.warn(
                "OAuth2 login rejected: email '{}' is not from allowed domain '{}'",
                email,
                ALLOWED_DOMAIN
            );
            response.sendRedirect(frontendUrl + "/?error=domain_not_allowed");
            return;
        }

        // Step 2: Email verification
        Boolean emailVerified = oidcUser.getEmailVerified();
        if (emailVerified == null || !emailVerified) {
            log.warn(
                "OAuth2 login rejected: email '{}' is not verified by Google",
                email
            );
            response.sendRedirect(frontendUrl + "/?error=email_not_verified");
            return;
        }

        // Step 3: Pre-provisioned user lookup — LOOKUP ONLY, no user creation
        String googleSub = oidcUser.getSubject();
        Users user = lookupPreProvisionedUser(googleSub, email, oidcUser);

        // Step 4: Account enabled check
        if (!user.isEnabled()) {
            log.warn(
                "OAuth2 login rejected: account for '{}' is disabled",
                email
            );
            response.sendRedirect(frontendUrl + "/?error=account_disabled");
            return;
        }

        // Step 5: Issue access token cookie
        String accessToken = jwtService.generateAccessToken(user);
        cookieUtil.setAccessTokenCookie(response, accessToken);

        // Step 6: Issue refresh token cookie
        String refreshToken = refreshTokenService.createRefreshToken(user);
        cookieUtil.setRefreshTokenCookie(response, refreshToken);

        log.info(
            "Successful OAuth2 login for '{}' (role: {})",
            email,
            user.getRole()
        );

        // Step 7: Invalidate the OAuth2 HTTP session.
        //
        // WHY: Spring Security stored the OidcUser authentication in the HTTP session
        // during the OAuth2 dance. If we allow this session to persist, every subsequent
        // API request from the frontend includes both the access_token cookie AND the
        // JSESSIONID cookie (because credentials: 'include' sends all matching cookies).
        // SecurityContextHolderFilter runs before JWTFilter, restores the OidcUser
        // authentication from the session, and JWTFilter then skips processing entirely.
        // AuthController gets a null UserPrincipal and returns 401.
        //
        // HOW: Invalidating the session removes the stored OidcUser authentication.
        // The next request arrives with a stale JSESSIONID; Spring finds no session,
        // SecurityContextHolder stays empty, and JWTFilter processes the access_token
        // cookie correctly.
        //
        // SAFETY: The access_token and refresh_token Set-Cookie headers are already
        // written to the response in steps 5 and 6. Invalidating the session cannot
        // remove headers that have already been added.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // Step 8: Clear the SecurityContext.
        //
        // WHY: SecurityContextHolderFilter saves the SecurityContext to a session in
        // its finally block at the END of filter chain processing. If we leave the
        // OidcUser authentication in the SecurityContext, it might be saved to a NEW
        // session (recreated by Spring after our invalidation), which would reproduce
        // the same JSESSIONID problem on the next request.
        //
        // Clearing the context ensures there is nothing to save — no new session
        // will be created for an empty SecurityContext.
        SecurityContextHolder.clearContext();

        // Step 9: Redirect to frontend callback page.
        // The callback page calls GET /api/auth/me, which now succeeds because
        // JWTFilter processes the access_token cookie without interference.
        response.sendRedirect(frontendUrl + "/auth/callback");
    }

    /**
     * Looks up a pre-provisioned user. NEVER creates a new record.
     *
     * On the first login of a pre-provisioned user (admin created the record
     * by email only), we back-fill googleSub and profile display fields on the
     * existing row. This is a controlled UPDATE of a record that already exists —
     * not user creation.
     *
     * @throws AccessDeniedException if the email is not found in the database,
     *         resulting in HTTP 403 Forbidden.
     */
    private Users lookupPreProvisionedUser(
        String googleSub,
        String email,
        OidcUser oidcUser
    ) {
        return usersRepository
            .findByGoogleSub(googleSub)
            .or(() ->
                usersRepository
                    .findByEmail(email)
                    .map(existing -> {
                        existing.setGoogleSub(googleSub);
                        existing.setName(oidcUser.getFullName());
                        existing.setAvatarUrl(oidcUser.getPicture());
                        return usersRepository.save(existing);
                    })
            )
            .orElseThrow(() -> {
                log.warn(
                    "OAuth2 login denied: email '{}' authenticated with Google " +
                        "but is not pre-provisioned in the system.",
                    email
                );
                return new AccessDeniedException(
                    "Access denied: your account has not been provisioned. " +
                        "Contact your administrator."
                );
            });
    }
}
