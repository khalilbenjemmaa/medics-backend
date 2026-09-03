package com.reemamiri.practice.security;

import com.reemamiri.practice.common.response.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reemamiri.practice.config.AppProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security policy.
 *
 * Three public endpoints, everything under /admin authenticated, and no
 * sessions at all. CSRF is disabled because there is no cookie to
 * forge against: the browser sends a bearer token it has to read from
 * storage and attach deliberately, which same-origin policy already
 * prevents a third-party site from doing.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // Public booking surface.
                    .requestMatchers(HttpMethod.GET, "/api/v1/availability").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/concern-categories").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/bookings").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/contact").permitAll()
                    // Operational and documentation.
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // Everything else, including all of /admin.
                    .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint((request, response, ex) ->
                            writeError(response, 401, "UNAUTHORIZED", "Authentication is required."))
                    .accessDeniedHandler((request, response, ex) ->
                            writeError(response, 403, "FORBIDDEN", "You do not have access to this resource.")))
            .headers(headers -> headers
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(contentType -> {})
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000)))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response,
                            int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiError.of(status, code, message, null));
    }

    /**
     * BCrypt at strength 12. Deliberately slow: the cost is paid once
     * per login by one person, and multiplied by every guess an
     * attacker with the hash would have to make.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // An explicit allow-list, never "*": these endpoints accept
        // credentials and personal data.
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
