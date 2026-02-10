package com.jobmonitor.platform.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * JWT token provider — stateless token creation and validation.
 * <p>
 * All secrets from {@link SecurityProperties} — zero hardcoded values.
 * Uses {@code Function<Claims, T>} for flexible claim extraction.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecurityProperties securityProperties;
    private final SecretKey signingKey;

    /** Functional claim extractor — avoids rigid method signatures */
    private final Function<String, Claims> claimsParser;

    public JwtTokenProvider(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.signingKey = Optional.ofNullable(securityProperties.getJwt().getSecret())
                .filter(s -> !s.isBlank())
                .map(s -> Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8)))
                .orElseThrow(() -> new IllegalStateException(
                        "JWT secret is not configured. Set AUTH_JWT_SECRET or auth.jwt.secret environment variable."));
        this.claimsParser = token -> Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Generate an access token.
     */
    public String generateToken(String userId, String username, String tenantId, Collection<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(securityProperties.getJwt().getExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .issuer(securityProperties.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("username", username)
                .claim("tenantId", tenantId)
                .claim("roles", roles)
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Generate a refresh token (longer-lived, minimal claims).
     */
    public String generateRefreshToken(String userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(securityProperties.getJwt().getRefreshExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .issuer(securityProperties.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("type", "refresh")
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Extract a specific claim using a functional extractor.
     */
    public <T> Optional<T> extractClaim(String token, Function<Claims, T> extractor) {
        return parseToken(token).map(extractor);
    }

    public Optional<String> extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Optional<String> extractUsername(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    public Optional<String> extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenantId", String.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", List.class))
                .orElse(Collections.emptyList());
    }

    /**
     * Validate token — returns true only if parseable and not expired.
     */
    public boolean validateToken(String token) {
        return parseToken(token).isPresent();
    }

    /**
     * Parse token returning Optional — functional error handling, no exceptions leak.
     */
    private Optional<Claims> parseToken(String token) {
        try {
            return Optional.of(claimsParser.apply(token));
        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("Malformed JWT: {}", ex.getMessage());
        } catch (SecurityException ex) {
            log.warn("Invalid JWT signature: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("Empty JWT: {}", ex.getMessage());
        }
        return Optional.empty();
    }
}
