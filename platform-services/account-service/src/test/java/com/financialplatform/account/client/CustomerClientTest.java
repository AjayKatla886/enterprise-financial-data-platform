package com.financialplatform.account.client;

import com.financialplatform.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CustomerClientTest {

    private MockRestServiceServer server;
    private CustomerClient customerClient;

    @BeforeEach
    void setUp() {

        RestClient.Builder builder =
                RestClient.builder();

        server = MockRestServiceServer
                .bindTo(builder)
                .build();

        customerClient = new CustomerClient(
                builder,
                "http://customer-service.test"
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
    void shouldPropagateCorrelationIdToCustomerService() {

        String correlationId =
                "day20-customer-client-test";

        MDC.put(
                CorrelationIdFilter.CORRELATION_ID_MDC_KEY,
                correlationId
        );

        server.expect(
                        requestTo(
                                "http://customer-service.test"
                                        + "/api/v1/customers/5"
                        )
                )
                .andExpect(method(HttpMethod.GET))
                .andExpect(
                        header(
                                CorrelationIdFilter
                                        .CORRELATION_ID_HEADER,
                                correlationId
                        )
                )
                .andRespond(
                        withSuccess(
                                validCustomerResponse(),
                                MediaType.APPLICATION_JSON
                        )
                );

        CustomerClient.CustomerLookupResponse customer =
                customerClient.getCustomerById(5L);

        assertEquals(
                5L,
                customer.customerId()
        );

        assertEquals(
                "ACTIVE",
                customer.customerStatus()
        );
    }

    private String validCustomerResponse() {

        return """
                {
                  "success": true,
                  "message": "Customer retrieved successfully",
                  "data": {
                    "customerId": 5,
                    "customerNumber": "CUS0000005",
                    "firstName": "John",
                    "lastName": "Doe",
                    "customerStatus": "ACTIVE"
                  }
                }
                """;
    }
}