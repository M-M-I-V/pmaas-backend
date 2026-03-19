package dev.mmiv.pmaas.configuration;

import dev.mmiv.pmaas.filter.JWTFilter;
import dev.mmiv.pmaas.security.JwtAuthEntryPoint;
import dev.mmiv.pmaas.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Central Spring Security configuration.
 * Changes applied across all phases:
 *   CorsConfig.java deleted. CORS configured here only, in one place.
 *   Allowed origins read from CORS_ALLOWED_ORIGINS env var.
 *   httpBasic() removed. JWT is the sole authentication mechanism.
 *   /uploads/** removed from permitAll(). All files now require auth.
 *   Served by FileController which sits inside the security filter chain.
 *   Spring Security 7 (migration): DaoAuthenticationProvider now requires
 *              UserDetailsService as a constructor argument. No-arg constructor
 *              and setUserDetailsService() were removed in Spring Security 7.
 *              throws Exception removed from securityFilterChain() and
 *              authenticationManager() — Spring Security 7 no longer declares
 *              checked exceptions on these methods.
 *   P3/P4 (this change): JwtAuthEntryPoint wired via exceptionHandling().
 *              Unauthenticated requests now return 401 JSON instead of Spring's
 *              default HTML error page or mismatched 403.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfiguration {

    private final CustomUserDetailsService customUserDetailsService;
    private final JWTFilter jwtFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOriginsRaw;

    public WebSecurityConfiguration(CustomUserDetailsService customUserDetailsService,
                                    JWTFilter jwtFilter,
                                    JwtAuthEntryPoint jwtAuthEntryPoint) {
        this.customUserDetailsService = customUserDetailsService;
        this.jwtFilter                = jwtFilter;
        this.jwtAuthEntryPoint        = jwtAuthEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/**").permitAll()
                        // All other requests — including /uploads/** — require authentication.
                        // Files are served by FileController which sits inside this filter chain.
                        .anyRequest().authenticated()
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // S-12 FIX: httpBasic() removed — JWT is the only authentication path.
                .exceptionHandling(ex ->
                        // P3/P4: Wire the entry point so unauthenticated requests get
                        // a structured 401 JSON response instead of an HTML error page.
                        ex.authenticationEntryPoint(jwtAuthEntryPoint)
                )
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
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Spring Security 7: UserDetailsService is a constructor argument; setter removed.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(new BCryptPasswordEncoder(10));
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }
}