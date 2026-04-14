package dev.mmiv.pmaas.configuration;

import dev.mmiv.pmaas.filter.JWTFilter;
import dev.mmiv.pmaas.security.JwtAuthEntryPoint;
import dev.mmiv.pmaas.security.OAuth2LoginFailureHandler;
import dev.mmiv.pmaas.security.OAuth2LoginSuccessHandler;
import java.util.Arrays;
import java.util.List;

import dev.mmiv.pmaas.security.RateLimitFilter;
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

/**
 * Spring Security configuration for OAuth2 + JWT authentication.
 *
 * CHANGE: accessDeniedHandler redirect target changed from
 * frontendUrl + "/login?error=user_not_provisioned" to
 * frontendUrl + "/?error=user_not_provisioned".
 *
 * The frontend's login page is at the root route (app/page.tsx), not at
 * /login. The previous redirect was producing a Next.js 404 whenever an
 * authenticated Google user was denied access because their email had not
 * been pre-provisioned in the system. Using "/?error=" routes the user
 * to the root login page where LoginForm renders the appropriate message.
 *
 * All other configuration is unchanged from the previous version.
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
    private final RateLimitFilter rateLimitFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOriginsRaw;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public WebSecurityConfiguration(
        JWTFilter jwtFilter,
        JwtAuthEntryPoint jwtAuthEntryPoint,
        OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
        OAuth2LoginFailureHandler oauth2LoginFailureHandler,
        RateLimitFilter rateLimitFilter
    ) {
        this.jwtFilter = jwtFilter;
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.oauth2LoginFailureHandler = oauth2LoginFailureHandler;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        CorsConfigurationSource corsConfigurationSource
    ) {
        http
            .authorizeHttpRequests(requests ->
                requests
                    // OAuth2 flow endpoints (Spring Security auto-generates these)
                    .requestMatchers("/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                    // Auth endpoints — refresh does not require an existing session
                    .requestMatchers("/api/auth/refresh")
                    .permitAll()
                    // Everything else requires authentication
                    .anyRequest()
                    .authenticated()
            )
            // Session management
            // IF_REQUIRED: Spring Security creates a session ONLY when needed for
            // the OAuth2 dance (storing state/nonce). API requests use JWT cookies.
            .sessionManagement(session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation()
                    .newSession()
                    .maximumSessions(5)
            )
            // CSRF disabled — mitigated by SameSite=Lax cookies + explicit CORS origins
            .csrf(AbstractHttpConfigurer::disable)
            // CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // OAuth2 login
            .oauth2Login(oauth2 ->
                oauth2
                    .authorizationEndpoint(auth ->
                        auth.baseUri("/oauth2/authorization")
                    )
                    .redirectionEndpoint(redirect ->
                        redirect.baseUri("/login/oauth2/code/*")
                    )
                    .successHandler(oauth2LoginSuccessHandler)
                    .failureHandler(oauth2LoginFailureHandler)
            )
            // Exception handling
            .exceptionHandling(ex ->
                ex
                    .authenticationEntryPoint(jwtAuthEntryPoint)
                    // 403: AccessDeniedException thrown during the OAuth2 callback
                    // (e.g. user not pre-provisioned in the system).
                    // CHANGE: redirect target changed from "/login?error=" to "/?error="
                    // to match the actual frontend route where LoginForm lives.
                    .accessDeniedHandler((request, response, ex2) -> {
                        log.warn(
                            "Access denied for request to '{}': {}",
                            request.getRequestURI(),
                            ex2.getMessage()
                        );
                        response.sendRedirect(
                            frontendUrl + "/?error=user_not_provisioned"
                        );
                    })
            )
            // JWT filter — reads access_token cookie on every API request
            .addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
            )
            .addFilterBefore(rateLimitFilter, JWTFilter.class);

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
        config.setAllowedMethods(
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );
        config.setAllowedHeaders(
            List.of("Authorization", "Content-Type", "X-Requested-With")
        );
        config.setExposedHeaders(List.of("Set-Cookie"));

        // allowCredentials=true is required for the browser to include httpOnly
        // cookies on cross-origin requests. REQUIRES explicit origins above.
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
