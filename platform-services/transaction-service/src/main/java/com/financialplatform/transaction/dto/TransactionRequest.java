package com.financialplatform.transaction.dto;

import com.financialplatform.transaction.entity.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransactionRequest(

        @NotNull(message = "Transaction type is required")
        TransactionType transactionType,

        @Positive(message = "Source account ID must be greater than zero")
        Long sourceAccountId,

        @Positive(message = "Target account ID must be greater than zero")
        Long targetAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(
                value = "0",
                inclusive = false,
                message = "Amount must be greater than zero"
        )
        @Digits(
                integer = 17,
                fraction = 2,
                message = "Amount must have at most 17 integer digits and 2 decimal places"
        )
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Pattern(
                regexp = "USD",
                message = "Only USD is supported currently"
        )
        String currency,

        @Size(
                max = 255,
                message = "Description must not exceed 255 characters"
        )
        String description

) {

    @AssertTrue(message = "Invalid account combination for transaction type")
    public boolean isAccountCombinationValid() {

        if (transactionType == null) {
            return true; // @NotNull reports the missing type.
        }

        return switch (transactionType) {
            case DEPOSIT ->
                    sourceAccountId == null && targetAccountId != null;

            case WITHDRAWAL ->
                    sourceAccountId != null && targetAccountId == null;

            case TRANSFER ->
                    sourceAccountId != null
                            && targetAccountId != null
                            && !sourceAccountId.equals(targetAccountId);
        };
    }
}