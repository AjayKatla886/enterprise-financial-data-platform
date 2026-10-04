package com.financialplatform.account.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountBalanceSummaryResponse(

        Long accountId,

        String accountNumber,

        String accountType,

        BigDecimal ledgerBalance,

        BigDecimal activeHoldAmount,

        BigDecimal availableBalance,

        LocalDateTime calculatedAt
) {
}