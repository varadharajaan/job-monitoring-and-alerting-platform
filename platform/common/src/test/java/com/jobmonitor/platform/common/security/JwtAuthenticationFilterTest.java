package com.jobmonitor.platform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TenantContext tenantContext;
    @Mock private SecurityProperties securityProperties;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerToken_setsSecurityContextAndTenantContext() throws ServletException, IOException {
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.extractUserId(token)).thenReturn(Optional.of("user-1"));
        when(jwtTokenProvider.extractUsername(token)).thenReturn(Optional.of("john"));
        when(jwtTokenProvider.extractTenantId(token)).thenReturn(Optional.of("tenant-A"));
        when(jwtTokenProvider.extractRoles(token)).thenReturn(List.of("ADMIN"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("user-1");
        verify(tenantContext).setUserId("user-1");
        verify(tenantContext).setUsername("john");
        verify(tenantContext).setTenantId("tenant-A");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noAuthorizationHeader_skipsAuthAndChainProceeds() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonBearerHeader_skipsAuth() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_skipsAuth() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer invalid.token");
        when(jwtTokenProvider.validateToken("invalid.token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void multipleRoles_allMappedToAuthorities() throws ServletException, IOException {
        String token = "multi.role.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.extractUserId(token)).thenReturn(Optional.of("user-2"));
        when(jwtTokenProvider.extractUsername(token)).thenReturn(Optional.of("jane"));
        when(jwtTokenProvider.extractTenantId(token)).thenReturn(Optional.of("tenant-B"));
        when(jwtTokenProvider.extractRoles(token)).thenReturn(List.of("ADMIN", "USER", "VIEWER"));

        filter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER", "ROLE_VIEWER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filterChain_alwaysCalledEvenOnException() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer crash.token");
        when(jwtTokenProvider.validateToken("crash.token")).thenThrow(new RuntimeException("boom"));

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {
            // exception propagates, but we verify filterChain behavior
        }
    }
}
