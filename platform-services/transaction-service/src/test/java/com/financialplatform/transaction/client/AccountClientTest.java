package com.financialplatform.transaction.client;

import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.transaction.exception.AccountServiceUnavailableException;
import com.financialplatform.transaction.exception.TransactionBusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;
import com.financialplatform.common.web.CorrelationIdFilter;
import com.financialplatform.common.web.CorrelationIdInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AccountClientTest {

    private MockRestServiceServer server;
    private AccountClient accountClient;

    @BeforeEach
    void setUp() {

        RestClient.Builder builder =
                RestClient.builder();

        /*
         * Bind the mock server before AccountClient clones
         * and builds the RestClient.
         */
        server = MockRestServiceServer
                .bindTo(builder)
                .build();

        ObjectMapper objectMapper =
                new ObjectMapper()
                        .findAndRegisterModules();

        String baseUrl =
                "http://account-service.test";

        accountClient = new AccountClient(
                builder,
                objectMapper,
                baseUrl
        );
    }

    @AfterEach
    void verifyRequests() {
        try {
            server.verify();
        } finally {
            MDC.clear();
        }
    }

    @Test
    void shouldRetrieveAccount() {
        expectLookup().andRespond(withSuccess(
                validResponse(),
                MediaType.APPLICATION_JSON
        ));

        AccountClient.AccountLookupResponse account =
                accountClient.getAccountById(21L);

        assertEquals(21L, account.accountId());
        assertEquals("ACTIVE", account.accountStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(account.balance()));
    }

    @Test
    void shouldMap404ToAccountNotFound() {
        expectLookup().andRespond(withResourceNotFound());

        TransactionBusinessException exception = assertThrows(
                TransactionBusinessException.class,
                () -> accountClient.getAccountById(21L)
        );

        assertEquals(
                ErrorCode.TRANSACTION_ACCOUNT_NOT_FOUND,
                exception.getErrorCode()
        );
    }

    @Test
    void shouldMapServerErrorToDependencyFailure() {
        expectLookup().andRespond(withServerError());

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.getAccountById(21L)
        );
    }

    @Test
    void shouldRejectMalformedJson() {
        expectLookup().andRespond(withSuccess(
                "{invalid-json",
                MediaType.APPLICATION_JSON
        ));

        AccountServiceUnavailableException exception = assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.getAccountById(21L)
        );

        assertNotNull(exception.getCause());
    }

    @Test
    void shouldRejectMissingData() {
        expectLookup().andRespond(withSuccess(
                """
                {
                  "success": true,
                  "message": "Account retrieved",
                  "data": null
                }
                """,
                MediaType.APPLICATION_JSON
        ));

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.getAccountById(21L)
        );
    }

    @Test
    void shouldRejectUnsuccessfulResponse() {
        expectLookup().andRespond(withSuccess(
                validResponse().replace(
                        "\"success\": true",
                        "\"success\": false"
                ),
                MediaType.APPLICATION_JSON
        ));

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.getAccountById(21L)
        );
    }

    @Test
    void shouldRejectMismatchedAccountId() {
        expectLookup().andRespond(withSuccess(
                validResponse().replace(
                        "\"accountId\": 21",
                        "\"accountId\": 22"
                ),
                MediaType.APPLICATION_JSON
        ));

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.getAccountById(21L)
        );
    }

    @Test
    void shouldRejectMissingBalance() {
        expectLookup().andRespond(withSuccess(
                validResponse().replace(
                        "\"balance\": 0.00",
                        "\"balance\": null"
                ),
                MediaType.APPLICATION_JSON
        ));

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.getAccountById(21L)
        );
    }

    @Test
    void shouldRejectMissingStatus() {
        expectLookup().andRespond(withSuccess(
                validResponse().replace(
                        "\"accountStatus\": \"ACTIVE\"",
                        "\"accountStatus\": null"
                ),
                MediaType.APPLICATION_JSON
        ));

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.getAccountById(21L)
        );
    }

    @Test
    void shouldMapTimeoutToDependencyFailure() {
        expectLookup().andRespond(withException(
                new SocketTimeoutException("Read timed out")
        ));

        AccountServiceUnavailableException exception = assertThrows(
                AccountServiceUnavailableException.class,
                () -> accountClient.getAccountById(21L)
        );

        assertNotNull(exception.getCause());
    }

    private ResponseActions expectLookup() {
        return server.expect(requestTo(
                        "http://account-service.test/api/v1/accounts/21"
                ))
                .andExpect(method(HttpMethod.GET));
    }

    @Test
    void shouldPropagateCorrelationIdToAccountService() {

        String correlationId = "day20-account-client-test";

        MDC.put(
                CorrelationIdFilter.CORRELATION_ID_MDC_KEY,
                correlationId
        );

        expectLookup()
                .andExpect(header(
                        CorrelationIdFilter.CORRELATION_ID_HEADER,
                        correlationId
                ))
                .andRespond(withSuccess(
                        validResponse(),
                        MediaType.APPLICATION_JSON
                ));

        AccountClient.AccountLookupResponse account =
                accountClient.getAccountById(21L);

        assertEquals(21L, account.accountId());
        assertEquals("ACTIVE", account.accountStatus());
    }

    private String validResponse() {
        return """
                {
                  "success": true,
                  "message": "Account retrieved successfully",
                  "data": {
                    "accountId": 21,
                    "accountNumber": "0000000021",
                    "customerId": 5,
                    "accountType": "SAVINGS",
                    "balance": 0.00,
                    "accountStatus": "ACTIVE",
                    "createdAt": null,
                    "updatedAt": null
                  }
                }
                """;
    }
}