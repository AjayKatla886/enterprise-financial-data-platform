package com.financialplatform.account.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long accountId,
        String accountNumber,
        Long customerId,
        String accountType,
        BigDecimal balance,
        String accountStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}