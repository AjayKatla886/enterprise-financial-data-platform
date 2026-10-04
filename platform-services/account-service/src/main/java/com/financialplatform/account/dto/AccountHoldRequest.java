package com.financialplatform.account.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountHoldRequest(

        @NotBlank(
                message = "Transaction reference is required"
        )
        @Size(
                max = 36,
                message = "Transaction reference must not exceed 36 characters"
        )
        String transactionReference,

        @NotNull(
                message = "Hold amount is required"
        )
        @Positive(
                message = "Hold amount must be greater than zero"
        )
        @Digits(
                integer = 17,
                fraction = 2,
                message = "Hold amount must have at most 17 integer digits and 2 decimal places"
        )
        BigDecimal amount,

        @Size(
                max = 255,
                message = "Description must not exceed 255 characters"
        )
        String description,

        @NotNull(
                message = "Hold expiration time is required"
        )
        @Future(
                message = "Hold expiration time must be in the future"
        )
        LocalDateTime expiresAt
) {
}