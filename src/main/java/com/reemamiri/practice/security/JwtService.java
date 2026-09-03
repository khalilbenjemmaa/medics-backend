package com.reemamiri.practice.security;

import com.reemamiri.practice.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies admin access tokens.
 *
 * A stateless signed token suits this application specifically: there
 * is one operator, no cross-device session list to manage, and no
 * server-side store worth running for a single user. The trade-off of
 * statelessness — a token cannot be revoked before it expires — is
 * bounded by keeping the lifetime short.
 *
 * The token carries a subject and nothing else. No email, no name, no
 * role list: a JWT is signed but not encrypted, so anything inside it
 * is readable by whoever holds it.
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenLifetime;

    public JwtService(AppProperties properties) {
        AppProperties.Security.Jwt config = properties.security().jwt();
        byte[] secret = config.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            // HS256 needs at least 256 bits. A short secret is a weak
            // signature, so this fails at startup rather than quietly
            // producing forgeable tokens.
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = config.issuer();
        this.accessTokenLifetime = Duration.ofMinutes(config.accessTokenMinutes());
    }

    public String issueAccessToken(AdminUser admin) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(admin.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenLifetime)))
                .signWith(key)
                .compact();
    }

    /** @return the admin id, or null if the token is absent, expired or forged. */
    public String parseSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception ex) {
            // Includes expiry and signature failure. Logged at debug
            // without the token, which is a credential.
            log.debug("Rejected a token: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    public long accessTokenSeconds() {
        return accessTokenLifetime.toSeconds();
    }
}
