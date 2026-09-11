package com.financialplatform.account.controller;

import com.financialplatform.account.dto.AccountRequest;
import com.financialplatform.account.dto.AccountResponse;
import com.financialplatform.account.exception.GlobalExceptionHandler;
import com.financialplatform.account.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest {

    private MockMvc mockMvc;
    private AccountService accountService;

    @BeforeEach
    void setUp() {

        accountService = mock(AccountService.class);

        AccountController accountController =
                new AccountController(accountService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(accountController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnVal001WhenCreateAccountRequestIsInvalid()
            throws Exception {

        String invalidRequest = """
                {
                  "customerId": 0,
                  "accountType": null
                }
                """;

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VAL-001"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

        verifyNoInteractions(accountService);
    }

    @Test
    void shouldCreateAccountSuccessfully()
            throws Exception {

        AccountResponse accountResponse =
                new AccountResponse(
                        25L,
                        "0000000025",
                        5L,
                        "CHECKING",
                        new BigDecimal("0.00"),
                        "ACTIVE",
                        LocalDateTime.now(),
                        LocalDateTime.now()
                );

        when(accountService.createAccount(any(AccountRequest.class)))
                .thenReturn(accountResponse);

        String validRequest = """
                {
                  "customerId": 5,
                  "accountType": "CHECKING"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Account created successfully"))
                .andExpect(jsonPath("$.data.accountId").value(25))
                .andExpect(jsonPath("$.data.accountNumber")
                        .value("0000000025"))
                .andExpect(jsonPath("$.data.customerId").value(5))
                .andExpect(jsonPath("$.data.accountType")
                        .value("CHECKING"))
                .andExpect(jsonPath("$.data.balance").value(0.00))
                .andExpect(jsonPath("$.data.accountStatus")
                        .value("ACTIVE"));

        verify(accountService)
                .createAccount(any(AccountRequest.class));
    }
}