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
import java.util.UUID;

/**
 * Servlet filter that populates MDC with traceId, tenantId, userId
 * for every HTTP request — these fields appear in all JSON log entries
 * automatically via logstash-logback-encoder.
 * <p>
 * If an X-Trace-Id header is present (from upstream gateway), it is reused;
 * otherwise a new UUID is generated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            var traceId = request.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }

            MDC.put("traceId", traceId);
            MDC.put("tenantId", nullSafe(request.getHeader(TENANT_ID_HEADER)));
            MDC.put("userId", nullSafe(request.getHeader(USER_ID_HEADER)));
            MDC.put("method", request.getMethod());
            MDC.put("uri", request.getRequestURI());

            response.setHeader(TRACE_ID_HEADER, traceId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
