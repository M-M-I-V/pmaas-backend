package dev.mmiv.pmaas.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

/**
 * Called when the OAuth2 flow fails — Google rejected the authentication,
 * or the success handler threw an exception (e.g. domain restriction).
 * Redirects to the login page with a safe error code.
 * Never exposes internal exception messages to the client.
 */
@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(@NonNull HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        @NonNull AuthenticationException exception) throws IOException {
        String errorCode = "auth_failed";

        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            String errorId = oauthEx.getError().getErrorCode();
            // Map known OAuth2 error codes to safe, frontend-consumable codes
            errorCode = switch (errorId) {
                case "access_denied"         -> "access_denied";
                case "domain_not_allowed"    -> "domain_not_allowed";
                case "account_disabled"      -> "account_disabled";
                case "email_not_verified"    -> "email_not_verified";
                default                      -> "auth_failed";
            };
        }

        // Log internally with the actual exception, but never expose it to the client
        log.warn("OAuth2 authentication failure — error: '{}' — message: '{}'",
                errorCode, exception.getMessage());

        response.sendRedirect(frontendUrl + "/login?error=" + errorCode);
    }
}