package dev.mmiv.pmaas.filter;

import dev.mmiv.pmaas.entity.UserPrincipal;
import dev.mmiv.pmaas.entity.Users;
import dev.mmiv.pmaas.repository.UsersRepository;
import dev.mmiv.pmaas.service.JWTService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * JWT authentication filter.
 * Reads the access token from the httpOnly cookie on every request.
 * If the token is valid, loads the user from the database and sets the
 * Spring Security authentication context.
 *
 * Updated from the original implementation:
 *   - Reads from httpOnly cookie (not Authorization header)
 *   - Looks up user by email (not username)
 *   - Handles JwtException gracefully (returns 401, not 500)
 *   - The role in the JWT is NOT trusted for @PreAuthorize decisions —
 *     the role is re-read from the database on every request via UserPrincipal
 *
 * Why re-read from DB on every request?
 *   If an admin downgrades a user's role, the change takes effect immediately.
 *   If the role were read from the JWT, it would remain elevated until the
 *   15-minute token expires. For a PHI system, immediate revocation matters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private final JWTService jwtService;
    private final UsersRepository usersRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Optional<String> tokenOpt = jwtService.extractTokenFromRequest(request);

        if (tokenOpt.isEmpty()) {
            // No token present — continue without setting authentication.
            // Spring Security will enforce access rules and return 401 if needed.
            filterChain.doFilter(request, response);
            return;
        }

        String token = tokenOpt.get();

        // Skip validation if SecurityContext already has authentication
        // (prevents redundant DB lookups on the same request)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String email = jwtService.extractEmail(token);

            // Load the user from DB — this ensures:
            //   1. The user still exists (not deleted)
            //   2. The account is still enabled (not deactivated)
            //   3. The role is current (not the potentially stale JWT claim)
            Users user = usersRepository.findByEmail(email).orElse(null);

            if (user != null && user.isEnabled() && !jwtService.isTokenExpired(token)) {
                UserPrincipal principal = new UserPrincipal(user);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        } catch (ExpiredJwtException ex) {
            // Token is expired — let the request proceed without authentication.
            // Spring Security will return 401, and the frontend interceptor will
            // attempt to refresh the token via /api/auth/refresh.
            log.debug("Access token expired for request to {}", request.getRequestURI());

        } catch (JwtException ex) {
            // Malformed or tampered token — log as a security warning
            log.warn("Invalid JWT on request to {}: {}", request.getRequestURI(), ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}