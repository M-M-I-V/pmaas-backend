package dev.mmiv.pmaas.security;

import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.repository.UsersRepository;
import dev.mmiv.pmaas.service.JWTService;
import dev.mmiv.pmaas.service.RefreshTokenService;
import dev.mmiv.pmaas.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Called by Spring Security after Google OIDC authentication succeeds.
 *
 * At this point, Spring Security has ALREADY validated the Google ID token:
 *   ✓ Signature verified against Google's JWKS endpoint
 *   ✓ Issuer = https://accounts.google.com
 *   ✓ Audience = our GOOGLE_CLIENT_ID
 *   ✓ Expiration is not past
 *   ✓ Nonce matches what was sent in the initial request
 *
 * This handler adds our application-specific checks and session management:
 *   1. Domain restriction — @mcst.edu.ph only (FIRST CHECK, fail fast)
 *   2. Email verification — Google must confirm the email is verified
 *   3. Pre-provisioned user lookup — LOOKUP ONLY, NO USER CREATION
 *        Users must be created in advance by an administrator.
 *        If the authenticated email is not found in the database, login is
 *        denied with AccessDeniedException → HTTP 403 Forbidden.
 *   4. Account enabled check
 *   5. Access token JWT issuance — short-lived (15 min), in httpOnly cookie
 *   6. Refresh token issuance — 7 days, stored in DB + httpOnly cookie
 *   7. Redirect to the React frontend callback page
 *
 * SECURITY: We NEVER redirect to a URL from the request. The frontend URL
 * is hardcoded via configuration. This prevents open redirect attacks.
 *
 * ADMIN WORKFLOW — how to grant access to a new user:
 *   INSERT INTO users (username, email, role, enabled)
 *   VALUES ('dr.smith@mcst.edu.ph', 'dr.smith@mcst.edu.ph', 'MD', true);
 *   The user's googleSub and profile data are recorded on their first successful login.
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
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        @NonNull Authentication authentication) throws IOException {
        // Safely check type and cast in one step. This proves oidcUser is not null.
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            log.error("OAuth2 login failed: Principal is missing or not an OidcUser");
            response.sendRedirect(frontendUrl + "/login?error=invalid_principal");
            return;
        }

        // Step 1: Domain restriction
        String email = oidcUser.getEmail();
        if (email == null || !email.toLowerCase().endsWith(ALLOWED_DOMAIN)) {
            log.warn("OAuth2 login rejected: email '{}' is not from allowed domain '{}'",
                    email, ALLOWED_DOMAIN);
            response.sendRedirect(frontendUrl + "/login?error=domain_not_allowed");
            return;
        }

        // Step 2: Email verification
        Boolean emailVerified = oidcUser.getEmailVerified();
        if (emailVerified == null || !emailVerified) {
            log.warn("OAuth2 login rejected: email '{}' is not verified by Google", email);
            response.sendRedirect(frontendUrl + "/login?error=email_not_verified");
            return;
        }

        // Step 3: Pre-provisioned user lookup — LOOKUP ONLY
        //
        // The system does NOT create users. Administrators pre-provision accounts.
        // Authentication is denied if the email is not found in the database.
        //
        // Lookup order:
        //   a) By googleSub — Google's stable, immutable user ID. Used after the
        //      first successful login once googleSub has been recorded.
        //   b) By email — Used on the very first login of a pre-provisioned user
        //      whose record was created by an admin before this login occurred.
        //      On this path, googleSub and profile fields are back-filled on the
        //      existing record (this is an UPDATE, not a CREATE).
        //   c) Neither found → AccessDeniedException → HTTP 403 Forbidden.
        String googleSub = oidcUser.getSubject();
        Users user = lookupPreProvisionedUser(googleSub, email, oidcUser);

        // Step 4: Account enabled check
        if (!user.isEnabled()) {
            log.warn("OAuth2 login rejected: account for '{}' is disabled", email);
            response.sendRedirect(frontendUrl + "/login?error=account_disabled");
            return;
        }

        // Step 5: Issue access token
        String accessToken = jwtService.generateAccessToken(user);
        cookieUtil.setAccessTokenCookie(response, accessToken);

        // Step 6: Issue refresh token
        String refreshToken = refreshTokenService.createRefreshToken(user);
        cookieUtil.setRefreshTokenCookie(response, refreshToken);

        log.info("Successful OAuth2 login for '{}' (role: {})", email, user.getRole());

        // Step 7: Redirect to frontend callback page
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
    private Users lookupPreProvisionedUser(String googleSub, String email, OidcUser oidcUser) {
        // Primary lookup: googleSub (set after the user's first login)
        return usersRepository.findByGoogleSub(googleSub)
                .or(() ->
                        // First-login fallback: email (admin pre-provisioned by email only)
                        usersRepository.findByEmail(email).map(existing -> {
                            // Back-fill stable Google ID and display fields on the existing record
                            existing.setGoogleSub(googleSub);
                            existing.setName(oidcUser.getFullName());
                            existing.setAvatarUrl(oidcUser.getPicture());
                            return usersRepository.save(existing);
                        })
                )
                .orElseThrow(() -> {
                    // The email passed domain validation but has no account in this system.
                    // The user must be provisioned by an administrator before they can log in.
                    log.warn("OAuth2 login denied: email '{}' authenticated with Google " +
                            "but is not pre-provisioned in the system.", email);
                    return new AccessDeniedException(
                            "Access denied: your account has not been provisioned. " +
                                    "Contact your administrator.");
                });
    }
}