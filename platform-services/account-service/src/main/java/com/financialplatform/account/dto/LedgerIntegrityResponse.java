package com.financialplatform.account.dto;

import com.financialplatform.account.entity.LedgerIntegrityStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LedgerIntegrityResponse(

        Long accountId,

        String accountNumber,

        BigDecimal storedAccountBalance,

        BigDecimal ledgerClosingBalance,

        long operationCount,

        boolean balanceMatches,

        boolean ledgerChainValid,

        boolean operationCalculationsValid,

        LedgerIntegrityStatus integrityStatus,

        List<String> issues,

        LocalDateTime checkedAt
) {
}