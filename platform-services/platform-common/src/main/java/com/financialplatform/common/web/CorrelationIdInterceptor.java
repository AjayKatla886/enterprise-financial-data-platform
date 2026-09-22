package com.financialplatform.common.web;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class CorrelationIdInterceptor
        implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution)
            throws IOException {

        String correlationId = MDC.get(
                CorrelationIdFilter.CORRELATION_ID_MDC_KEY
        );

        if (correlationId != null
                && !correlationId.isBlank()) {

            request.getHeaders().set(
                    CorrelationIdFilter.CORRELATION_ID_HEADER,
                    correlationId
            );
        }

        return execution.execute(request, body);
    }
}