package com.jobmonitor.platform.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET = "this-is-a-very-secure-jwt-test-secret-key-that-is-at-least-64-bytes-long-for-HS512";
    private JwtTokenProvider tokenProvider;
    private SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.getJwt().setSecret(SECRET);
        securityProperties.getJwt().setExpiration(Duration.ofHours(1));
        securityProperties.getJwt().setRefreshExpiration(Duration.ofDays(1));
        securityProperties.getJwt().setIssuer("test-issuer");
        tokenProvider = new JwtTokenProvider(securityProperties);
    }

    @Test
    void generateToken_shouldReturnValidJwt() {
        String token = tokenProvider.generateToken("user-1", "john", "tenant-A", List.of("ADMIN", "USER"));
        assertThat(token).isNotBlank();
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void extractUserId_shouldReturnSubject() {
        String token = tokenProvider.generateToken("user-42", "jane", "tenant-B", List.of("USER"));
        assertThat(tokenProvider.extractUserId(token)).isEqualTo(Optional.of("user-42"));
    }

    @Test
    void extractUsername_shouldReturnClaim() {
        String token = tokenProvider.generateToken("u1", "alice", "t1", List.of());
        assertThat(tokenProvider.extractUsername(token)).isEqualTo(Optional.of("alice"));
    }

    @Test
    void extractTenantId_shouldReturnClaim() {
        String token = tokenProvider.generateToken("u1", "bob", "tenant-xyz", List.of());
        assertThat(tokenProvider.extractTenantId(token)).isEqualTo(Optional.of("tenant-xyz"));
    }

    @Test
    void extractRoles_shouldReturnAllRoles() {
        String token = tokenProvider.generateToken("u1", "carol", "t1", List.of("ADMIN", "VIEWER"));
        assertThat(tokenProvider.extractRoles(token)).containsExactly("ADMIN", "VIEWER");
    }

    @Test
    void extractRoles_shouldReturnEmptyListForInvalidToken() {
        assertThat(tokenProvider.extractRoles("invalid.token.here")).isEmpty();
    }

    @Test
    void validateToken_shouldReturnFalseForMalformedToken() {
        assertThat(tokenProvider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void validateToken_shouldReturnFalseForExpiredToken() {
        SecurityProperties props = new SecurityProperties();
        props.getJwt().setSecret(SECRET);
        props.getJwt().setExpiration(Duration.ofMillis(1)); // expires immediately
        JwtTokenProvider shortLived = new JwtTokenProvider(props);

        String token = shortLived.generateToken("u1", "test", "t1", List.of());
        // small sleep to ensure expiry
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        assertThat(shortLived.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_shouldReturnFalseForTamperedToken() {
        String token = tokenProvider.generateToken("u1", "test", "t1", List.of());
        // Tamper by modifying header/payload part (keep the dot structure)
        String[] parts = token.split("\\.");
        // Flip a character in payload
        char[] payloadChars = parts[1].toCharArray();
        payloadChars[0] = payloadChars[0] == 'a' ? 'b' : 'a';
        String tampered = parts[0] + "." + new String(payloadChars) + "." + parts[2];
        assertThat(tokenProvider.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_shouldReturnFalseForWrongSecret() {
        // Create token with different secret (must be >= 64 bytes for HS512)
        SecurityProperties otherProps = new SecurityProperties();
        otherProps.getJwt().setSecret("completely-different-jwt-secret-key-that-is-also-at-least-64-bytes-long-for-HS512-requirements-xyz");
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProps);
        String token = otherProvider.generateToken("u1", "test", "t1", List.of());

        assertThat(tokenProvider.validateToken(token)).isFalse();
    }

    @Test
    void generateRefreshToken_shouldBeValid() {
        String refreshToken = tokenProvider.generateRefreshToken("user-99");
        assertThat(tokenProvider.validateToken(refreshToken)).isTrue();
        assertThat(tokenProvider.extractUserId(refreshToken)).isEqualTo(Optional.of("user-99"));
    }

    @Test
    void constructor_shouldThrowOnBlankSecret() {
        SecurityProperties props = new SecurityProperties();
        props.getJwt().setSecret("   ");
        assertThatThrownBy(() -> new JwtTokenProvider(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret is not configured");
    }

    @Test
    void constructor_shouldThrowOnNullSecret() {
        SecurityProperties props = new SecurityProperties();
        props.getJwt().setSecret(null);
        assertThatThrownBy(() -> new JwtTokenProvider(props))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void extractClaim_shouldReturnEmptyForInvalidToken() {
        Optional<String> result = tokenProvider.extractClaim("bad.token", Claims::getSubject);
        assertThat(result).isEmpty();
    }
}
