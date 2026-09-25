package com.financialplatform.transaction.service;

import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
import com.financialplatform.transaction.entity.TransactionStatusHistory;
import com.financialplatform.transaction.entity.TransactionTransitionSource;
import com.financialplatform.transaction.entity.TransactionType;
import com.financialplatform.transaction.repository.TransactionRepository;
import com.financialplatform.transaction.repository.TransactionStatusHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionStatusTransitionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionStatusHistoryRepository
            transactionStatusHistoryRepository;

    @InjectMocks
    private TransactionStatusTransitionService
            transitionService;

    @AfterEach
    void clearCorrelationId() {
        MDC.clear();
    }

    @Test
    void shouldSaveInitialTransactionAndHistory() {

        MDC.put(
                "correlationId",
                "day22-correlation-001"
        );

        Transaction transaction =
                newTransaction();

        when(transactionRepository.saveAndFlush(transaction))
                .thenAnswer(invocation -> {
                    Transaction saved =
                            invocation.getArgument(0);

                    saved.setTransactionId(101L);

                    return saved;
                });

        when(transactionStatusHistoryRepository
                .saveAndFlush(
                        any(TransactionStatusHistory.class)
                ))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        Transaction result =
                transitionService.saveInitialTransaction(
                        transaction,
                        TransactionTransitionSource
                                .TRANSACTION_SUBMISSION,
                        "Transaction request recorded"
                );

        assertEquals(
                101L,
                result.getTransactionId()
        );

        assertEquals(
                TransactionStatus.PENDING,
                result.getTransactionStatus()
        );

        ArgumentCaptor<TransactionStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(
                        TransactionStatusHistory.class
                );

        verify(transactionStatusHistoryRepository)
                .saveAndFlush(
                        historyCaptor.capture()
                );

        TransactionStatusHistory history =
                historyCaptor.getValue();

        assertEquals(
                101L,
                history.getTransactionId()
        );

        assertEquals(
                transaction.getTransactionReference(),
                history.getTransactionReference()
        );

        assertNull(
                history.getPreviousStatus()
        );

        assertEquals(
                TransactionStatus.PENDING,
                history.getNewStatus()
        );

        assertEquals(
                TransactionTransitionSource
                        .TRANSACTION_SUBMISSION,
                history.getTransitionSource()
        );

        assertEquals(
                "Transaction request recorded",
                history.getTransitionReason()
        );

        assertEquals(
                "day22-correlation-001",
                history.getCorrelationId()
        );

        assertNotNull(
                history.getCreatedAt()
        );

        verify(transactionRepository)
                .saveAndFlush(transaction);
    }

    @Test
    void shouldUpdateStatusAndRecordHistory() {

        Transaction transaction =
                savedTransaction(
                        TransactionStatus.PENDING
                );

        when(transactionRepository.saveAndFlush(transaction))
                .thenReturn(transaction);

        when(transactionStatusHistoryRepository
                .saveAndFlush(
                        any(TransactionStatusHistory.class)
                ))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        LocalDateTime originalUpdatedAt =
                transaction.getUpdatedAt();

        Transaction result =
                transitionService.transition(
                        transaction,
                        TransactionStatus.PROCESSING,
                        TransactionTransitionSource
                                .TRANSACTION_PROCESSOR,
                        "Transaction execution started"
                );

        assertSame(
                transaction,
                result
        );

        assertEquals(
                TransactionStatus.PROCESSING,
                result.getTransactionStatus()
        );

        assertNotNull(
                result.getUpdatedAt()
        );

        assertFalse(
                result.getUpdatedAt()
                        .isBefore(originalUpdatedAt)
        );

        ArgumentCaptor<TransactionStatusHistory> historyCaptor =
                ArgumentCaptor.forClass(
                        TransactionStatusHistory.class
                );

        verify(transactionStatusHistoryRepository)
                .saveAndFlush(
                        historyCaptor.capture()
                );

        TransactionStatusHistory history =
                historyCaptor.getValue();

        assertEquals(
                TransactionStatus.PENDING,
                history.getPreviousStatus()
        );

        assertEquals(
                TransactionStatus.PROCESSING,
                history.getNewStatus()
        );

        assertEquals(
                TransactionTransitionSource
                        .TRANSACTION_PROCESSOR,
                history.getTransitionSource()
        );

        assertEquals(
                "Transaction execution started",
                history.getTransitionReason()
        );

        verify(transactionRepository)
                .saveAndFlush(transaction);
    }

    @Test
    void shouldNotCreateDuplicateHistoryForSameStatus() {

        Transaction transaction =
                savedTransaction(
                        TransactionStatus.COMPLETED
                );

        Transaction result =
                transitionService.transition(
                        transaction,
                        TransactionStatus.COMPLETED,
                        TransactionTransitionSource
                                .RECONCILIATION_API,
                        "Transaction already completed"
                );

        assertSame(
                transaction,
                result
        );

        verifyNoInteractions(
                transactionRepository
        );

        verifyNoInteractions(
                transactionStatusHistoryRepository
        );
    }

    @Test
    void shouldRejectUnsavedTransactionTransition() {

        Transaction transaction =
                newTransaction();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> transitionService.transition(
                                transaction,
                                TransactionStatus.PROCESSING,
                                TransactionTransitionSource
                                        .TRANSACTION_PROCESSOR,
                                "Transaction execution started"
                        )
                );

        assertEquals(
                "Transaction must be saved before changing its status",
                exception.getMessage()
        );

        verifyNoInteractions(
                transactionRepository
        );

        verifyNoInteractions(
                transactionStatusHistoryRepository
        );
    }

    @Test
    void shouldRejectReasonLongerThanDatabaseLimit() {

        Transaction transaction =
                savedTransaction(
                        TransactionStatus.PENDING
                );

        String reason =
                "x".repeat(501);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> transitionService.transition(
                                transaction,
                                TransactionStatus.PROCESSING,
                                TransactionTransitionSource
                                        .TRANSACTION_PROCESSOR,
                                reason
                        )
                );

        assertEquals(
                "Transaction transition reason must not exceed "
                        + "500 characters",
                exception.getMessage()
        );

        assertEquals(
                TransactionStatus.PENDING,
                transaction.getTransactionStatus()
        );

        verifyNoInteractions(
                transactionRepository
        );

        verifyNoInteractions(
                transactionStatusHistoryRepository
        );
    }

    private Transaction newTransaction() {

        LocalDateTime now =
                LocalDateTime.now();

        return Transaction.builder()
                .transactionReference(
                        "11111111-2222-3333-4444-555555555555"
                )
                .idempotencyKey(
                        "day22-test-key"
                )
                .requestHash(
                        "test-request-hash"
                )
                .transactionType(
                        TransactionType.DEPOSIT
                )
                .targetAccountId(21L)
                .amount(
                        new BigDecimal("25.00")
                )
                .currency("USD")
                .transactionStatus(
                        TransactionStatus.PENDING
                )
                .description(
                        "Day 22 unit test"
                )
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Transaction savedTransaction(
            TransactionStatus status) {

        Transaction transaction =
                newTransaction();

        transaction.setTransactionId(101L);
        transaction.setTransactionStatus(status);

        return transaction;
    }
}