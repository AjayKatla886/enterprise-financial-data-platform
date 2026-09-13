package com.financialplatform.account.dto;

import com.financialplatform.account.entity.BalanceOperationType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BalanceOperationRequest(

        @NotBlank(message = "Transaction reference is required")
        @Size(
                max = 36,
                message = "Transaction reference must not exceed 36 characters"
        )
        String transactionReference,

        @NotNull(message = "Balance operation type is required")
        BalanceOperationType operationType,

        @NotNull(message = "Amount is required")
        @DecimalMin(
                value = "0.01",
                message = "Amount must be greater than zero"
        )
        @Digits(
                integer = 17,
                fraction = 2,
                message = "Amount must contain no more than 17 integer digits and 2 decimal places"
        )
        BigDecimal amount,

        @Size(
                max = 255,
                message = "Description must not exceed 255 characters"
        )
        String description

) {
}