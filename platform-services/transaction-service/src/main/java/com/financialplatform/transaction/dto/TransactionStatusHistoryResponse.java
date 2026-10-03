package com.financialplatform.transaction.dto;

import com.financialplatform.transaction.entity.TransactionStatus;
import com.financialplatform.transaction.entity.TransactionStatusHistory;
import com.financialplatform.transaction.entity.TransactionTransitionSource;

import java.time.LocalDateTime;

public record TransactionStatusHistoryResponse(

        Long statusHistoryId,

        Long transactionId,

        String transactionReference,

        TransactionStatus previousStatus,

        TransactionStatus newStatus,

        TransactionTransitionSource transitionSource,

        String transitionReason,

        String correlationId,

        LocalDateTime createdAt

) {

    public static TransactionStatusHistoryResponse from(
            TransactionStatusHistory history) {

        return new TransactionStatusHistoryResponse(
                history.getStatusHistoryId(),
                history.getTransactionId(),
                history.getTransactionReference(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getTransitionSource(),
                history.getTransitionReason(),
                history.getCorrelationId(),
                history.getCreatedAt()
        );
    }
}