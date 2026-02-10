package com.jobmonitor.platform.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Populates MDC context from incoming HTTP headers for structured JSON logging.
 * Uses Optional and functional extraction to avoid null handling boilerplate.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String USER_ID_HEADER = "X-User-Id";

    /** MDC keys mapped to their extraction logic — functional, no null checks scattered. */
    private static final Map<String, Function<HttpServletRequest, String>> MDC_EXTRACTORS = Map.of(
            "traceId",  req -> extractHeader(req, TRACE_ID_HEADER)
                                 .orElseGet(() -> UUID.randomUUID().toString().replace("-", "")),
            "tenantId", req -> extractHeader(req, TENANT_ID_HEADER).orElse(""),
            "userId",   req -> extractHeader(req, USER_ID_HEADER).orElse(""),
            "method",   HttpServletRequest::getMethod,
            "uri",      HttpServletRequest::getRequestURI
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            MDC_EXTRACTORS.forEach((key, extractor) -> MDC.put(key, extractor.apply(request)));

            Optional.ofNullable(MDC.get("traceId"))
                    .ifPresent(traceId -> response.setHeader(TRACE_ID_HEADER, traceId));

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static Optional<String> extractHeader(HttpServletRequest request, String headerName) {
        return Optional.ofNullable(request.getHeader(headerName))
                .filter(value -> !value.isBlank());
    }
}
