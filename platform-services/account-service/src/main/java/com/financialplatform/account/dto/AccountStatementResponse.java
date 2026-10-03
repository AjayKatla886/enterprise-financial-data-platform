package com.financialplatform.account.dto;

import com.financialplatform.common.response.PageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountStatementResponse(

        Long accountId,

        String accountNumber,

        String accountType,

        LocalDateTime fromDate,

        LocalDateTime toDate,

        BigDecimal openingBalance,

        BigDecimal closingBalance,

        BigDecimal totalCredits,

        BigDecimal totalDebits,

        long creditCount,

        long debitCount,

        PageResponse<BalanceOperationResponse> operations
) {
}