package com.jobmonitor.platform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * JWT authentication filter — extracts token from Authorization header,
 * validates it, and populates Spring Security context + {@link TenantContext}.
 * <p>
 * Uses functional composition for header extraction and validation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TenantContext tenantContext;
    private final SecurityProperties securityProperties;

    /** Functional token extractor from Authorization header */
    private final Function<HttpServletRequest, Optional<String>> tokenExtractor =
            request -> Optional.ofNullable(request.getHeader("Authorization"))
                    .filter(header -> header.startsWith("Bearer "))
                    .map(header -> header.substring(7));

    /** Predicate: is this request already authenticated? */
    private final Predicate<Void> isNotAuthenticated =
            ignored -> SecurityContextHolder.getContext().getAuthentication() == null;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        tokenExtractor.apply(request)
                .filter(jwtTokenProvider::validateToken)
                .ifPresent(token -> {
                    if (SecurityContextHolder.getContext().getAuthentication() == null) {
                        authenticateFromToken(token, request);
                    }
                });

        filterChain.doFilter(request, response);
    }

    private void authenticateFromToken(String token, HttpServletRequest request) {
        jwtTokenProvider.extractUserId(token).ifPresent(userId -> {
            var authorities = jwtTokenProvider.extractRoles(token).stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            var authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Populate tenant context
            tenantContext.setUserId(userId);
            jwtTokenProvider.extractUsername(token).ifPresent(tenantContext::setUsername);
            jwtTokenProvider.extractTenantId(token).ifPresent(tenantContext::setTenantId);

            log.debug("Authenticated user={} tenant={}", userId,
                    tenantContext.getTenantIdOptional().orElse("none"));
        });
    }
}
