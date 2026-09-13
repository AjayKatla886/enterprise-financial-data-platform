package com.financialplatform.account.dto;

import com.financialplatform.account.entity.BalanceOperation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BalanceOperationResponse(

        Long balanceOperationId,
        String operationReference,
        String transactionReference,
        Long accountId,
        String operationType,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String description,
        LocalDateTime createdAt

) {

    public static BalanceOperationResponse from(
            BalanceOperation operation) {

        return new BalanceOperationResponse(
                operation.getBalanceOperationId(),
                operation.getOperationReference(),
                operation.getTransactionReference(),
                operation.getAccountId(),
                operation.getOperationType().name(),
                operation.getAmount(),
                operation.getBalanceBefore(),
                operation.getBalanceAfter(),
                operation.getDescription(),
                operation.getCreatedAt()
        );
    }
}