package com.reemamiri.practice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads a bearer token and, if it verifies, authenticates the request.
 *
 * The account is re-read on every request rather than trusted from the
 * token, so deactivating the admin takes effect immediately instead of
 * whenever the last issued token happens to expire.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminUserRepository adminUserRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String subject = jwtService.parseSubject(header.substring(7));
            if (subject != null) {
                adminUserRepository.findById(UUID.fromString(subject))
                        .filter(AdminUser::isActive)
                        .ifPresent(admin -> {
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    admin.getEmail(), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                            authentication.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            }
        }
        chain.doFilter(request, response);
    }

    /** Public endpoints never carry a token; skip the work entirely. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/admin") || path.startsWith("/api/v1/admin/auth/login");
    }
}
