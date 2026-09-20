package com.financialplatform.transaction.scheduler;

import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
import com.financialplatform.transaction.entity.TransactionType;
import com.financialplatform.transaction.repository.TransactionRepository;
import com.financialplatform.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionReconciliationSchedulerTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 50;
    private static final long MINIMUM_AGE_SECONDS = 30L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransactionReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {

        ReflectionTestUtils.setField(
                scheduler,
                "minimumAgeSeconds",
                MINIMUM_AGE_SECONDS
        );

        ReflectionTestUtils.setField(
                scheduler,
                "batchSize",
                BATCH_SIZE
        );

        ReflectionTestUtils.setField(
                scheduler,
                "maxAttempts",
                MAX_ATTEMPTS
        );
    }

    @Test
    void shouldDoNothingWhenNoTransactionsAreEligible() {

        when(transactionRepository.findReconciliationCandidates(
                eq(reconciliationStatuses()),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(PageRequest.of(0, BATCH_SIZE))
        )).thenReturn(List.of());

        scheduler.reconcileUnresolvedTransactions();

        verify(transactionRepository)
                .findReconciliationCandidates(
                        eq(reconciliationStatuses()),
                        any(LocalDateTime.class),
                        eq(MAX_ATTEMPTS),
                        eq(PageRequest.of(0, BATCH_SIZE))
                );

        verifyNoInteractions(transactionService);

        verify(
                transactionRepository,
                never()
        ).saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldCompleteAutomaticallyReconciledTransaction() {

        Transaction candidate = buildTransaction(
                TransactionStatus.RECONCILIATION_REQUIRED,
                0
        );

        Transaction completedTransaction = buildTransaction(
                TransactionStatus.COMPLETED,
                1
        );

        when(transactionRepository.findReconciliationCandidates(
                eq(reconciliationStatuses()),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(PageRequest.of(0, BATCH_SIZE))
        )).thenReturn(List.of(candidate));

        when(transactionService.reconcileTransaction(
                candidate.getTransactionReference()
        )).thenReturn(completedTransaction);

        scheduler.reconcileUnresolvedTransactions();

        assertEquals(
                1,
                candidate.getReconciliationAttempts()
        );

        assertNotNull(
                candidate.getLastReconciliationAt()
        );

        verify(transactionRepository)
                .saveAndFlush(candidate);

        verify(transactionService)
                .reconcileTransaction(
                        candidate.getTransactionReference()
                );

        verifyNoMoreInteractions(transactionService);
    }

    @Test
    void shouldKeepTransactionUnresolvedBeforeMaximumAttempts() {

        Transaction candidate = buildTransaction(
                TransactionStatus.RECONCILIATION_REQUIRED,
                2
        );

        when(transactionRepository.findReconciliationCandidates(
                eq(reconciliationStatuses()),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(PageRequest.of(0, BATCH_SIZE))
        )).thenReturn(List.of(candidate));

        when(transactionService.reconcileTransaction(
                candidate.getTransactionReference()
        )).thenReturn(candidate);

        scheduler.reconcileUnresolvedTransactions();

        assertEquals(
                TransactionStatus.RECONCILIATION_REQUIRED,
                candidate.getTransactionStatus()
        );

        assertEquals(
                3,
                candidate.getReconciliationAttempts()
        );

        assertNotNull(
                candidate.getLastReconciliationAt()
        );

        assertNull(
                candidate.getFailureReason()
        );

        verify(transactionRepository)
                .saveAndFlush(candidate);

        verify(transactionService)
                .reconcileTransaction(
                        candidate.getTransactionReference()
                );
    }

    @Test
    void shouldMoveUnresolvedTransactionToManualReviewAtMaximumAttempts() {

        Transaction candidate = buildTransaction(
                TransactionStatus.RECONCILIATION_REQUIRED,
                4
        );

        when(transactionRepository.findReconciliationCandidates(
                eq(reconciliationStatuses()),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(PageRequest.of(0, BATCH_SIZE))
        )).thenReturn(List.of(candidate));

        when(transactionService.reconcileTransaction(
                candidate.getTransactionReference()
        )).thenReturn(candidate);

        scheduler.reconcileUnresolvedTransactions();

        assertEquals(
                MAX_ATTEMPTS,
                candidate.getReconciliationAttempts()
        );

        assertEquals(
                TransactionStatus.MANUAL_REVIEW,
                candidate.getTransactionStatus()
        );

        assertEquals(
                "Automatic reconciliation attempts exhausted; "
                        + "manual review is required",
                candidate.getFailureReason()
        );

        assertNotNull(
                candidate.getLastReconciliationAt()
        );

        assertNotNull(
                candidate.getUpdatedAt()
        );

        verify(
                transactionRepository,
                times(2)
        ).saveAndFlush(candidate);

        verify(transactionService)
                .reconcileTransaction(
                        candidate.getTransactionReference()
                );
    }

    @Test
    void shouldMoveTransactionToManualReviewWhenReconciliationThrowsAtMaximumAttempts() {

        Transaction candidate = buildTransaction(
                TransactionStatus.PROCESSING,
                4
        );

        when(transactionRepository.findReconciliationCandidates(
                eq(reconciliationStatuses()),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(PageRequest.of(0, BATCH_SIZE))
        )).thenReturn(List.of(candidate));

        when(transactionService.reconcileTransaction(
                candidate.getTransactionReference()
        )).thenThrow(
                new RuntimeException(
                        "Account Service unavailable"
                )
        );

        scheduler.reconcileUnresolvedTransactions();

        assertEquals(
                MAX_ATTEMPTS,
                candidate.getReconciliationAttempts()
        );

        assertEquals(
                TransactionStatus.MANUAL_REVIEW,
                candidate.getTransactionStatus()
        );

        assertEquals(
                "Automatic reconciliation attempts exhausted; "
                        + "manual review is required",
                candidate.getFailureReason()
        );

        verify(
                transactionRepository,
                times(2)
        ).saveAndFlush(candidate);
    }

    @Test
    void shouldContinueRetryingWhenReconciliationThrowsBeforeMaximumAttempts() {

        Transaction candidate = buildTransaction(
                TransactionStatus.PROCESSING,
                1
        );

        when(transactionRepository.findReconciliationCandidates(
                eq(reconciliationStatuses()),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(PageRequest.of(0, BATCH_SIZE))
        )).thenReturn(List.of(candidate));

        when(transactionService.reconcileTransaction(
                candidate.getTransactionReference()
        )).thenThrow(
                new RuntimeException(
                        "Temporary dependency failure"
                )
        );

        scheduler.reconcileUnresolvedTransactions();

        assertEquals(
                2,
                candidate.getReconciliationAttempts()
        );

        assertEquals(
                TransactionStatus.PROCESSING,
                candidate.getTransactionStatus()
        );

        assertNull(
                candidate.getFailureReason()
        );

        assertNotNull(
                candidate.getLastReconciliationAt()
        );

        verify(transactionRepository)
                .saveAndFlush(candidate);

        verify(transactionService)
                .reconcileTransaction(
                        candidate.getTransactionReference()
                );
    }

    @Test
    void shouldTreatNullReconciliationAttemptsAsZero() {

        Transaction candidate = buildTransaction(
                TransactionStatus.RECONCILIATION_REQUIRED,
                null
        );

        Transaction completedTransaction = buildTransaction(
                TransactionStatus.COMPLETED,
                1
        );

        when(transactionRepository.findReconciliationCandidates(
                eq(reconciliationStatuses()),
                any(LocalDateTime.class),
                eq(MAX_ATTEMPTS),
                eq(PageRequest.of(0, BATCH_SIZE))
        )).thenReturn(List.of(candidate));

        when(transactionService.reconcileTransaction(
                candidate.getTransactionReference()
        )).thenReturn(completedTransaction);

        scheduler.reconcileUnresolvedTransactions();

        assertEquals(
                1,
                candidate.getReconciliationAttempts()
        );

        assertNotNull(
                candidate.getLastReconciliationAt()
        );

        verify(transactionRepository)
                .saveAndFlush(candidate);
    }

    private List<TransactionStatus> reconciliationStatuses() {

        return List.of(
                TransactionStatus.PROCESSING,
                TransactionStatus.RECONCILIATION_REQUIRED
        );
    }

    private Transaction buildTransaction(
            TransactionStatus status,
            Integer reconciliationAttempts) {

        LocalDateTime now = LocalDateTime.now();

        return Transaction.builder()
                .transactionId(41L)
                .transactionReference(
                        "42b9c42c-5ec6-45cc-b384-ec9a155b2b4c"
                )
                .idempotencyKey(
                        "day18-reconciliation-test"
                )
                .requestHash(
                        "test-request-hash"
                )
                .transactionType(
                        TransactionType.DEPOSIT
                )
                .sourceAccountId(null)
                .targetAccountId(21L)
                .amount(
                        new BigDecimal("100.00")
                )
                .currency("USD")
                .transactionStatus(status)
                .description(
                        "Day 18 automatic reconciliation test"
                )
                .reconciliationAttempts(
                        reconciliationAttempts
                )
                .createdAt(now.minusMinutes(5))
                .updatedAt(now.minusMinutes(2))
                .build();
    }
}