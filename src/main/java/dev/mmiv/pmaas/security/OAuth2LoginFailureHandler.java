package dev.mmiv.pmaas.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Called when the OAuth2 flow fails — Google rejected the authentication,
 * or the success handler threw an exception (e.g. domain restriction).
 * Redirects to the login page with a safe error code.
 * Never exposes internal exception messages to the client.
 *
 * CHANGE: redirect target changed from FRONTEND_URL + "/login?error=" to
 * FRONTEND_URL + "/?error=". The frontend's login page lives at the root
 * route (app/page.tsx), not at /login. There is no /login route in the
 * Next.js app, so the previous redirect produced a 404 when any OAuth2
 * failure occurred. Using "/?error=" lands the user directly on the
 * LoginForm component, which reads the ?error= query parameter and
 * displays a specific, actionable message for each error code.
 */
@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull AuthenticationException exception
    ) throws IOException {
        String errorCode = "auth_failed";

        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            String errorId = oauthEx.getError().getErrorCode();
            // Map known OAuth2 error codes to safe, frontend-consumable codes
            errorCode = switch (errorId) {
                case "access_denied" -> "access_denied";
                case "domain_not_allowed" -> "domain_not_allowed";
                case "account_disabled" -> "account_disabled";
                case "email_not_verified" -> "email_not_verified";
                default -> "auth_failed";
            };
        }

        // Log internally with the actual exception, but never expose it to the client
        log.warn(
            "OAuth2 authentication failure — error: '{}' — message: '{}'",
            errorCode,
            exception.getMessage()
        );

        // Redirect to the root login page with the error code as a query parameter.
        // The LoginForm component at app/page.tsx reads ?error= and displays a
        // human-readable message for each code.
        response.sendRedirect(frontendUrl + "/?error=" + errorCode);
    }
}
