package com.financialplatform.transaction.service;

import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
import com.financialplatform.transaction.entity.TransactionStatusHistory;
import com.financialplatform.transaction.entity.TransactionTransitionSource;
import com.financialplatform.transaction.repository.TransactionRepository;
import com.financialplatform.transaction.repository.TransactionStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionStatusTransitionService {

    private static final String CORRELATION_ID_KEY =
            "correlationId";

    private static final int MAX_REASON_LENGTH = 500;

    private final TransactionRepository transactionRepository;

    private final TransactionStatusHistoryRepository
            transactionStatusHistoryRepository;

    /**
     * Saves a new transaction and creates its initial status-history
     * record in the same database transaction.
     */
    @Transactional
    public Transaction saveInitialTransaction(
            Transaction transaction,
            TransactionTransitionSource source,
            String reason) {

        validateNewTransaction(transaction);
        validateSource(source);
        String normalizedReason =
                normalizeReason(reason);

        Transaction savedTransaction =
                transactionRepository.saveAndFlush(
                        transaction
                );

        TransactionStatusHistory history =
                buildHistory(
                        savedTransaction,
                        null,
                        savedTransaction.getTransactionStatus(),
                        source,
                        normalizedReason
                );

        transactionStatusHistoryRepository.saveAndFlush(
                history
        );

        log.info(
                "Initial transaction status recorded. "
                        + "transactionId={}, reference={}, "
                        + "status={}, source={}",
                savedTransaction.getTransactionId(),
                savedTransaction.getTransactionReference(),
                savedTransaction.getTransactionStatus(),
                source
        );

        return savedTransaction;
    }

    /**
     * Updates an existing transaction status and inserts its
     * status-history record in the same database transaction.
     */
    @Transactional
    public Transaction transition(
            Transaction transaction,
            TransactionStatus newStatus,
            TransactionTransitionSource source,
            String reason) {

        validateExistingTransaction(transaction);
        validateStatus(newStatus);
        validateSource(source);
        String normalizedReason =
                normalizeReason(reason);

        TransactionStatus previousStatus =
                transaction.getTransactionStatus();

        if (Objects.equals(
                previousStatus,
                newStatus
        )) {

            log.debug(
                    "Transaction already has requested status. "
                            + "transactionId={}, reference={}, status={}",
                    transaction.getTransactionId(),
                    transaction.getTransactionReference(),
                    newStatus
            );

            return transaction;
        }

        LocalDateTime now =
                LocalDateTime.now();

        transaction.setTransactionStatus(
                newStatus
        );

        transaction.setUpdatedAt(now);

        Transaction savedTransaction =
                transactionRepository.saveAndFlush(
                        transaction
                );

        TransactionStatusHistory history =
                buildHistory(
                        savedTransaction,
                        previousStatus,
                        newStatus,
                        source,
                        normalizedReason
                );

        transactionStatusHistoryRepository.saveAndFlush(
                history
        );

        log.info(
                "Transaction status changed. "
                        + "transactionId={}, reference={}, "
                        + "previousStatus={}, newStatus={}, source={}",
                savedTransaction.getTransactionId(),
                savedTransaction.getTransactionReference(),
                previousStatus,
                newStatus,
                source
        );

        return savedTransaction;
    }

    private TransactionStatusHistory buildHistory(
            Transaction transaction,
            TransactionStatus previousStatus,
            TransactionStatus newStatus,
            TransactionTransitionSource source,
            String reason) {

        return TransactionStatusHistory.builder()
                .transactionId(
                        transaction.getTransactionId()
                )
                .transactionReference(
                        transaction.getTransactionReference()
                )
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .transitionSource(source)
                .transitionReason(reason)
                .correlationId(
                        getCorrelationId()
                )
                .createdAt(
                        LocalDateTime.now()
                )
                .build();
    }

    private void validateNewTransaction(
            Transaction transaction) {

        if (transaction == null) {
            throw new IllegalArgumentException(
                    "Transaction is required"
            );
        }

        if (transaction.getTransactionId() != null) {
            throw new IllegalArgumentException(
                    "New transaction must not already have an ID"
            );
        }

        if (transaction.getTransactionReference() == null
                || transaction.getTransactionReference().isBlank()) {

            throw new IllegalArgumentException(
                    "Transaction reference is required"
            );
        }

        if (transaction.getTransactionStatus() == null) {
            throw new IllegalArgumentException(
                    "Initial transaction status is required"
            );
        }
    }

    private void validateExistingTransaction(
            Transaction transaction) {

        if (transaction == null) {
            throw new IllegalArgumentException(
                    "Transaction is required"
            );
        }

        if (transaction.getTransactionId() == null) {
            throw new IllegalArgumentException(
                    "Transaction must be saved before changing its status"
            );
        }

        if (transaction.getTransactionReference() == null
                || transaction.getTransactionReference().isBlank()) {

            throw new IllegalArgumentException(
                    "Transaction reference is required"
            );
        }

        if (transaction.getTransactionStatus() == null) {
            throw new IllegalArgumentException(
                    "Current transaction status is required"
            );
        }
    }

    private void validateStatus(
            TransactionStatus status) {

        if (status == null) {
            throw new IllegalArgumentException(
                    "New transaction status is required"
            );
        }
    }

    private void validateSource(
            TransactionTransitionSource source) {

        if (source == null) {
            throw new IllegalArgumentException(
                    "Transaction transition source is required"
            );
        }
    }

    private String normalizeReason(
            String reason) {

        if (reason == null
                || reason.isBlank()) {

            return null;
        }

        String normalizedReason =
                reason.trim();

        if (normalizedReason.length()
                > MAX_REASON_LENGTH) {

            throw new IllegalArgumentException(
                    "Transaction transition reason must not exceed "
                            + MAX_REASON_LENGTH
                            + " characters"
            );
        }

        return normalizedReason;
    }

    private String getCorrelationId() {

        String correlationId =
                MDC.get(CORRELATION_ID_KEY);

        if (correlationId == null
                || correlationId.isBlank()) {

            return null;
        }

        return correlationId.trim();
    }
}