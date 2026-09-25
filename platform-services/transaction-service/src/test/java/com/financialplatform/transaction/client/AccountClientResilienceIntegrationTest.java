package com.financialplatform.transaction.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialplatform.transaction.exception.AccountServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes =
                AccountClientResilienceIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "resilience4j.retry.instances.accountService.max-attempts=3",
                "resilience4j.retry.instances.accountService.wait-duration=10ms",
                "resilience4j.retry.instances.accountService.retry-exceptions="
                        + "com.financialplatform.transaction.exception."
                        + "AccountServiceUnavailableException",
                "resilience4j.retry.instances.accountService.ignore-exceptions="
                        + "com.financialplatform.transaction.exception."
                        + "TransactionBusinessException",

                "resilience4j.circuitbreaker.instances.accountService."
                        + "sliding-window-type=COUNT_BASED",
                "resilience4j.circuitbreaker.instances.accountService."
                        + "sliding-window-size=6",
                "resilience4j.circuitbreaker.instances.accountService."
                        + "minimum-number-of-calls=6",
                "resilience4j.circuitbreaker.instances.accountService."
                        + "failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.accountService."
                        + "wait-duration-in-open-state=1s",
                "resilience4j.circuitbreaker.instances.accountService."
                        + "permitted-number-of-calls-in-half-open-state=1",
                "resilience4j.circuitbreaker.instances.accountService."
                        + "automatic-transition-from-open-to-half-open-enabled=true",
                "resilience4j.circuitbreaker.instances.accountService."
                        + "record-exceptions="
                        + "com.financialplatform.transaction.exception."
                        + "AccountServiceUnavailableException",
                "resilience4j.circuitbreaker.instances.accountService."
                        + "ignore-exceptions="
                        + "com.financialplatform.transaction.exception."
                        + "TransactionBusinessException"
        }
)
class AccountClientResilienceIntegrationTest {

    private static final AtomicInteger REQUEST_COUNT =
            new AtomicInteger();

    private static final AtomicInteger FAILURES_BEFORE_SUCCESS =
            new AtomicInteger();

    private static HttpServer httpServer;
    private static int serverPort;

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @BeforeAll
    static void startServer() throws IOException {

        httpServer = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        httpServer.createContext(
                "/api/v1/accounts/21",
                exchange -> {

                    REQUEST_COUNT.incrementAndGet();

                    if (FAILURES_BEFORE_SUCCESS.getAndDecrement() > 0) {

                        byte[] response =
                                """
                                {
                                  "success": false,
                                  "message": "Temporary failure",
                                  "data": null
                                }
                                """.getBytes(StandardCharsets.UTF_8);

                        exchange.getResponseHeaders().add(
                                "Content-Type",
                                "application/json"
                        );

                        exchange.sendResponseHeaders(
                                500,
                                response.length
                        );

                        try (OutputStream output =
                                     exchange.getResponseBody()) {

                            output.write(response);
                        }

                        return;
                    }

                    byte[] response =
                            validAccountResponse()
                                    .getBytes(StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().add(
                            "Content-Type",
                            "application/json"
                    );

                    exchange.sendResponseHeaders(
                            200,
                            response.length
                    );

                    try (OutputStream output =
                                 exchange.getResponseBody()) {

                        output.write(response);
                    }
                }
        );

        httpServer.start();
        serverPort = httpServer.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {

        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @DynamicPropertySource
    static void accountServiceProperties(
            DynamicPropertyRegistry registry) {

        registry.add(
                "services.account.base-url",
                () -> "http://localhost:" + serverPort
        );
    }

    @BeforeEach
    void resetState() {

        REQUEST_COUNT.set(0);
        FAILURES_BEFORE_SUCCESS.set(0);

        circuitBreakerRegistry
                .circuitBreaker("accountService")
                .reset();

        retryRegistry
                .retry("accountService")
                .getEventPublisher();
    }

    @Test
    void shouldRetryTemporaryFailuresAndThenSucceed() {

        FAILURES_BEFORE_SUCCESS.set(2);

        AccountClient.AccountLookupResponse account =
                accountClient.getAccountById(21L);

        assertNotNull(account);
        assertEquals(21L, account.accountId());
        assertEquals("ACTIVE", account.accountStatus());

        /*
         * Initial request + two retry attempts.
         */
        assertEquals(3, REQUEST_COUNT.get());
    }

    @Test
    void shouldOpenCircuitAfterRepeatedFailures() {

        /*
         * Every HTTP request will fail.
         */
        FAILURES_BEFORE_SUCCESS.set(Integer.MAX_VALUE);

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.getAccountById(21L)
        );

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.getAccountById(21L)
        );

        assertEquals(
                io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN,
                circuitBreakerRegistry
                        .circuitBreaker("accountService")
                        .getState()
        );

        int requestsBeforeRejectedCall =
                REQUEST_COUNT.get();

        /*
         * The open circuit rejects this call without contacting
         * the HTTP server.
         */
        assertThrows(
                CallNotPermittedException.class,
                () -> accountClient.getAccountById(21L)
        );

        assertEquals(
                requestsBeforeRejectedCall,
                REQUEST_COUNT.get()
        );
    }

    private static String validAccountResponse() {

        return """
                {
                  "success": true,
                  "message": "Account retrieved successfully",
                  "data": {
                    "accountId": 21,
                    "accountNumber": "0000000021",
                    "customerId": 5,
                    "accountType": "SAVINGS",
                    "balance": 100.00,
                    "accountStatus": "ACTIVE",
                    "createdAt": null,
                    "updatedAt": null
                  }
                }
                """;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
            exclude = {
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    FlywayAutoConfiguration.class
            }
    )
    @Import(AccountClient.class)
    static class TestApplication {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper()
                    .findAndRegisterModules();
        }
    }
}