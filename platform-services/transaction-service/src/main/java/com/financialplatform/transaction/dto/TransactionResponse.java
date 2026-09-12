package com.financialplatform.transaction.dto;

import com.financialplatform.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long transactionId,
        String transactionReference,
        String transactionType,
        Long sourceAccountId,
        Long targetAccountId,
        BigDecimal amount,
        String currency,
        String transactionStatus,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getTransactionReference(),
                transaction.getTransactionType().name(),
                transaction.getSourceAccountId(),
                transaction.getTargetAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getTransactionStatus().name(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}