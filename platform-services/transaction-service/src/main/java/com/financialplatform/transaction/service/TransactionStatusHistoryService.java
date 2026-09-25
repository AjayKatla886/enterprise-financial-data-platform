package com.financialplatform.transaction.service;

import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.transaction.entity.TransactionStatusHistory;
import com.financialplatform.transaction.exception.TransactionBusinessException;
import com.financialplatform.transaction.repository.TransactionRepository;
import com.financialplatform.transaction.repository.TransactionStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionStatusHistoryService {

    private final TransactionRepository transactionRepository;

    private final TransactionStatusHistoryRepository
            transactionStatusHistoryRepository;

    @Transactional(readOnly = true)
    public List<TransactionStatusHistory>
    getTransactionHistory(
            String transactionReference) {

        String normalizedReference =
                validateAndNormalizeReference(
                        transactionReference
                );

        /*
         * Verify the transaction exists before retrieving history.
         *
         * An existing transaction created before Day 22 may
         * legitimately have no history records.
         */
        transactionRepository
                .findByTransactionReference(
                        normalizedReference
                )
                .orElseThrow(() ->
                        new TransactionBusinessException(
                                ErrorCode.TRANSACTION_NOT_FOUND,
                                "Transaction not found with reference: "
                                        + normalizedReference
                        )
                );

        return transactionStatusHistoryRepository
                .findByTransactionReferenceOrderByCreatedAtAscStatusHistoryIdAsc(
                        normalizedReference
                );
    }

    private String validateAndNormalizeReference(
            String transactionReference) {

        if (transactionReference == null
                || transactionReference.isBlank()) {

            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Transaction reference is required"
            );
        }

        return transactionReference.trim();
    }
}