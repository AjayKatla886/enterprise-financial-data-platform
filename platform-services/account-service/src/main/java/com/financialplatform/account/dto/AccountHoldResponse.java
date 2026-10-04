package com.financialplatform.account.dto;

import com.financialplatform.account.entity.AccountHold;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountHoldResponse(

        Long accountHoldId,

        String holdReference,

        String transactionReference,

        Long accountId,

        BigDecimal amount,

        String holdStatus,

        String description,

        LocalDateTime expiresAt,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,

        LocalDateTime resolvedAt
) {

    public static AccountHoldResponse from(
            AccountHold hold) {

        return new AccountHoldResponse(
                hold.getAccountHoldId(),
                hold.getHoldReference(),
                hold.getTransactionReference(),
                hold.getAccountId(),
                hold.getAmount(),
                hold.getHoldStatus().name(),
                hold.getDescription(),
                hold.getExpiresAt(),
                hold.getCreatedAt(),
                hold.getUpdatedAt(),
                hold.getResolvedAt()
        );
    }
}