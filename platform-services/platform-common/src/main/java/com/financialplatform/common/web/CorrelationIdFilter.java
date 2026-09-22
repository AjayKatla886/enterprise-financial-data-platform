package com.financialplatform.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter
        extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER =
            "X-Correlation-ID";

    public static final String CORRELATION_ID_MDC_KEY =
            "correlationId";

    private static final int MAX_CORRELATION_ID_LENGTH = 100;

    private static final Pattern VALID_CORRELATION_ID =
            Pattern.compile("^[A-Za-z0-9._:-]+$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId =
                resolveCorrelationId(
                        request.getHeader(
                                CORRELATION_ID_HEADER
                        )
                );

        MDC.put(
                CORRELATION_ID_MDC_KEY,
                correlationId
        );

        response.setHeader(
                CORRELATION_ID_HEADER,
                correlationId
        );

        try {
            filterChain.doFilter(
                    request,
                    response
            );
        } finally {
            MDC.remove(
                    CORRELATION_ID_MDC_KEY
            );
        }
    }

    private String resolveCorrelationId(
            String requestedCorrelationId) {

        if (requestedCorrelationId == null
                || requestedCorrelationId.isBlank()) {

            return UUID.randomUUID().toString();
        }

        String normalizedCorrelationId =
                requestedCorrelationId.trim();

        if (normalizedCorrelationId.length()
                > MAX_CORRELATION_ID_LENGTH) {

            return UUID.randomUUID().toString();
        }

        if (!VALID_CORRELATION_ID
                .matcher(normalizedCorrelationId)
                .matches()) {

            return UUID.randomUUID().toString();
        }

        return normalizedCorrelationId;
    }
}