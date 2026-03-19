package dev.mmiv.pmaas.security;

import tools.jackson.databind.ObjectMapper;
import dev.mmiv.pmaas.dto.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns a structured JSON 401 response when an unauthenticated request reaches
 * a secured endpoint (missing token, invalid token, expired token).
 * * WHY THIS IS NEEDED:
 * Spring Security's default AuthenticationEntryPoint returns an HTML 401 page or
 * a 403 Forbidden — the exact behaviour depends on the Spring Security version and
 * whether a WWW-Authenticate header is set. Neither is appropriate for a REST API
 * consumed by a React frontend.
 * * Without this, an expired JWT causes JWTFilter to skip authentication, Spring
 * Security detects no Authentication in the SecurityContext, and the default
 * entrypoint fires — returning an HTML error page or a 403 that the frontend
 * misinterprets as "authenticated but not authorised", triggering the wrong UI flow.
 * * WITH THIS:
 * Every unauthenticated request to a secured endpoint returns exactly:
 * HTTP 401 — Content-Type: application/json
 * {
 * "status":    401,
 * "error":     "Unauthorized",
 * "message":   "Authentication required. Please log in.",
 * "path":      "/api/patients",
 * "timestamp": "2026-03-18T10:30:00"
 * }
 * * The frontend can then check for 401 and redirect to the login screen.
 * * WIRING: Registered in WebSecurityConfiguration via:
 * http.exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
 */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    // Jackson 3 supports Java 8 Date/Time natively. No module registration needed!
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(
            @NonNull HttpServletRequest request,         // Annotated with @NonNull
            @NonNull HttpServletResponse response,       // Annotated with @NonNull
            @NonNull AuthenticationException authException // Annotated with @NonNull
    ) throws IOException, ServletException {         // Added ServletException here

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.of(
                401,
                "Unauthorized",
                "Authentication required. Please log in.",
                request.getRequestURI()
        );

        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}