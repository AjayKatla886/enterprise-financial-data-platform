package com.financialplatform.account.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountTransferRequest(

        @NotNull(message = "Source account ID is required")
        @Positive(message = "Source account ID must be greater than zero")
        Long sourceAccountId,

        @NotNull(message = "Target account ID is required")
        @Positive(message = "Target account ID must be greater than zero")
        Long targetAccountId,

        @NotBlank(message = "Transaction reference is required")
        @Size(
                max = 36,
                message = "Transaction reference must not exceed 36 characters"
        )
        String transactionReference,

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

    @AssertTrue(
            message = "Source and target accounts must be different"
    )
    public boolean isDifferentAccount() {

        if (sourceAccountId == null
                || targetAccountId == null) {
            return true;
        }

        return !sourceAccountId.equals(targetAccountId);
    }
}