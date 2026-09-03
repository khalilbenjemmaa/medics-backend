package com.reemamiri.practice.security;

import com.reemamiri.practice.common.exception.ApiException;
import com.reemamiri.practice.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticates the operator, and bootstraps the account on first run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService implements ApplicationRunner {

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    public record LoginResult(String accessToken, long expiresInSeconds, String displayName) {}

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        // The same failure for an unknown account and a wrong password.
        // Distinguishing them tells an attacker which addresses exist.
        var admin = repository.findByEmailIgnoreCaseAndActiveTrue(email)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        log.info("Admin login succeeded");
        return new LoginResult(
                jwtService.issueAccessToken(admin),
                jwtService.accessTokenSeconds(),
                admin.getDisplayName());
    }

    /**
     * Changes the operator's password.
     *
     * Requires the current one: the caller is authenticated, but an
     * unattended screen should not be enough to lock the real owner out
     * of their own account.
     *
     * The new hash invalidates nothing already issued — these tokens are
     * stateless and cannot be revoked before they expire. That window is
     * bounded by the short lifetime, and is the trade-off statelessness
     * buys; it is called out here so it is a known property rather than
     * a surprise.
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        AdminUser admin = repository.findByEmailIgnoreCaseAndActiveTrue(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            log.warn("Password change rejected: current password did not match");
            throw new BadCredentialsException("Invalid credentials");
        }
        if (passwordEncoder.matches(newPassword, admin.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED",
                    "The new password must be different from the current one.");
        }

        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        repository.save(admin);
        log.info("Admin password changed");
    }

    /**
     * Creates the operator account on first start.
     *
     * Requires ADMIN_PASSWORD to be supplied. There is deliberately no
     * default password: a well-known default on an unattended
     * deployment is an open door, and a system holding patient contact
     * details should refuse to start rather than ship one.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.security().admin().email();
        if (repository.existsByEmailIgnoreCase(email)) {
            return;
        }
        String password = properties.security().admin().password();
        if (password == null || password.isBlank()) {
            log.warn("No admin account exists and ADMIN_PASSWORD is not set. "
                    + "Set ADMIN_EMAIL and ADMIN_PASSWORD, then restart, to create it.");
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDisplayName("Practice administrator");
        repository.save(admin);
        log.info("Created the initial admin account for {}", email);
    }
}
