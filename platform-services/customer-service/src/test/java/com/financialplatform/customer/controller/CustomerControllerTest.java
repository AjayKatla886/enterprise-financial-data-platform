package com.financialplatform.customer.controller;

import com.financialplatform.customer.exception.GlobalExceptionHandler;
import com.financialplatform.customer.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.financialplatform.customer.dto.CustomerRequest;
import com.financialplatform.customer.dto.CustomerResponse;
import static org.mockito.Mockito.verify;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerControllerTest {

    private MockMvc mockMvc;
    private CustomerService customerService;

    @BeforeEach
    void setUp() {

        customerService = mock(CustomerService.class);

        CustomerController customerController =
                new CustomerController(customerService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(customerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnVal001WhenCreateCustomerRequestIsInvalid()
            throws Exception {

        String invalidRequest = """
                {
                  "firstName": "",
                  "lastName": "",
                  "email": "invalid-email",
                  "phoneNumber": "123",
                  "dateOfBirth": "2030-01-01"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VAL-001"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/customers"));

        verifyNoInteractions(customerService);
    }
    @Test
    void shouldCreateCustomerSuccessfully()
            throws Exception {

        CustomerResponse customerResponse =
                new CustomerResponse(
                        10L,
                        "000010",
                        "Ajay",
                        "Katla",
                        "ajay@example.com",
                        "2145551234",
                        LocalDate.of(1995, 5, 15),
                        "ACTIVE",
                        LocalDateTime.now(),
                        LocalDateTime.now()
                );

        when(customerService.createCustomer(any(CustomerRequest.class)))
                .thenReturn(customerResponse);

        String validRequest = """
            {
              "firstName": "Ajay",
              "lastName": "Katla",
              "email": "ajay@example.com",
              "phoneNumber": "2145551234",
              "dateOfBirth": "1995-05-15"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Customer created successfully"))
                .andExpect(jsonPath("$.data.customerId").value(10))
                .andExpect(jsonPath("$.data.customerNumber").value("000010"))
                .andExpect(jsonPath("$.data.firstName").value("Ajay"))
                .andExpect(jsonPath("$.data.lastName").value("Katla"))
                .andExpect(jsonPath("$.data.email")
                        .value("ajay@example.com"))
                .andExpect(jsonPath("$.data.customerStatus")
                        .value("ACTIVE"));

        verify(
                customerService
        ).createCustomer(any(CustomerRequest.class));
    }
}