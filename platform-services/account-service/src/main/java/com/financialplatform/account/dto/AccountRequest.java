package com.financialplatform.account.dto;

import com.financialplatform.account.entity.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AccountRequest(

        @NotNull(message = "Customer ID is required")
        @Positive(message = "Customer ID must be greater than zero")
        Long customerId,

        @NotNull(message = "Account type is required")
        AccountType accountType

) {
}