package dev.mmiv.pmaas.configuration;

import dev.mmiv.pmaas.filter.JWTFilter;
import dev.mmiv.pmaas.security.JwtAuthEntryPoint;
import dev.mmiv.pmaas.security.OAuth2LoginFailureHandler;
import dev.mmiv.pmaas.security.OAuth2LoginSuccessHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for OAuth2 + JWT authentication.
 *
 * KEY CHANGES from the password-based version:
 *
 *   1. DaoAuthenticationProvider REMOVED — no more password-based login.
 *      There is no /api/auth/login endpoint. Authentication is entirely
 *      delegated to Google via OAuth2.
 *
 *   2. oauth2Login() ADDED — Spring Security handles the full OAuth2/OIDC
 *      dance automatically (redirect, code exchange, ID token validation).
 *
 *   3. Session policy changed from STATELESS to IF_REQUIRED.
 *      Spring Security needs a server-side session for the brief OAuth2 dance
 *      (to store the state parameter and nonce for verification at callback).
 *      After the callback, JWT cookies handle all subsequent authentication.
 *      The session is not used for API requests.
 *
 *   4. CORS allowCredentials changed to TRUE.
 *      Required for the browser to include httpOnly cookies on cross-origin
 *      requests from the React frontend. When allowCredentials=true, the
 *      allowed origins MUST be explicit — no wildcard ("*").
 *
 *   5. CSRF remains DISABLED for the API.
 *      SameSite=Lax cookies + explicit CORS origins provide equivalent
 *      protection for the API endpoints. Spring Security's OAuth2 client
 *      uses the 'state' parameter to protect the OAuth2 flow itself.
 *
 *   6. JwtAuthEntryPoint returns structured 401 JSON (not HTML).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class WebSecurityConfiguration {

    private final JWTFilter jwtFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oauth2LoginFailureHandler;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOriginsRaw;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public WebSecurityConfiguration(JWTFilter jwtFilter,
                                    JwtAuthEntryPoint jwtAuthEntryPoint,
                                    OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
                                    OAuth2LoginFailureHandler oauth2LoginFailureHandler) {
        this.jwtFilter                  = jwtFilter;
        this.jwtAuthEntryPoint          = jwtAuthEntryPoint;
        this.oauth2LoginSuccessHandler  = oauth2LoginSuccessHandler;
        this.oauth2LoginFailureHandler  = oauth2LoginFailureHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) {
        http
                .authorizeHttpRequests(requests -> requests
                        // OAuth2 flow endpoints (Spring Security auto-generates these)
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // Auth endpoints — me and logout require auth; refresh does not (it validates its own token)
                        .requestMatchers("/api/auth/refresh").permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // Session management
                // IF_REQUIRED: Spring Security creates a session ONLY when needed for
                // the OAuth2 dance (storing state/nonce). API requests use JWT cookies.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                .sessionFixation().newSession()  // Invalidate old session on login
                                .maximumSessions(5)              // Max concurrent sessions per user
                )

                // CSRF
                // Disabled for the API. Mitigations in place:
                //   - SameSite=Lax cookies block cross-site POST requests
                //   - CORS with explicit origins blocks unauthorized cross-origin requests
                //   - OAuth2 state parameter protects the OAuth flow itself
                .csrf(AbstractHttpConfigurer::disable)

                // CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // OAuth2 login
                .oauth2Login(oauth2 -> oauth2
                        // Spring Security auto-generates /oauth2/authorization/google
                        // as the endpoint that initiates the OAuth2 flow
                        .authorizationEndpoint(auth ->
                                auth.baseUri("/oauth2/authorization")
                        )
                        // Spring Security handles the callback at /login/oauth2/code/google
                        // (matches the redirect URI registered in Google Cloud Console)
                        .redirectionEndpoint(redirect ->
                                redirect.baseUri("/login/oauth2/code/*")
                        )
                        // Our handler: domain check + user provisioning + JWT issuance
                        .successHandler(oauth2LoginSuccessHandler)
                        // Our failure handler: safe redirect with error code
                        .failureHandler(oauth2LoginFailureHandler)
                )

                // Exception handling
                .exceptionHandling(ex -> ex
                        // 401: unauthenticated API requests → structured JSON
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        // 403: AccessDeniedException thrown during the OAuth2 callback
                        //      (e.g. user not pre-provisioned). The browser is mid-redirect,
                        //      so we send it to the login page with a clear error code rather
                        //      than returning a raw Spring 403 HTML page.
                        .accessDeniedHandler((request, response, ex2) -> {
                            log.warn("Access denied for request to '{}': {}",
                                    request.getRequestURI(), ex2.getMessage());
                            response.sendRedirect(frontendUrl + "/login?error=user_not_provisioned");
                        })
                )

                // JWT filter
                // Reads access_token cookie on every API request
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Set-Cookie"));

        // CRITICAL: allowCredentials=true is required for the browser to include
        // httpOnly cookies on cross-origin requests from the React frontend.
        // This REQUIRES explicit origins above — wildcard ("*") is rejected by browsers.
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}