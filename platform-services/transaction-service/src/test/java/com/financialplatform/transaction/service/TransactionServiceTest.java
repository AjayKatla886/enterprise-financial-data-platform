package com.financialplatform.transaction.service;

import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.transaction.client.AccountClient;
import com.financialplatform.transaction.dto.TransactionRequest;
import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
import com.financialplatform.transaction.entity.TransactionType;
import com.financialplatform.transaction.exception.AccountServiceUnavailableException;
import com.financialplatform.transaction.exception.TransactionBusinessException;
import com.financialplatform.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String IDEMPOTENCY_KEY =
            "transaction-request-001";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountClient accountClient;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void shouldProcessDepositAsCompleted() {

        when(transactionRepository.findByIdempotencyKey(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(transactionRepository.saveAndFlush(
                any(Transaction.class)
        )).thenAnswer(invocation -> {

            Transaction transaction =
                    invocation.getArgument(0);

            transaction.setTransactionId(1L);

            return transaction;
        });

        Transaction saved =
                transactionService.submitTransaction(
                        IDEMPOTENCY_KEY,
                        request(
                                TransactionType.DEPOSIT,
                                null,
                                21L
                        )
                );

        assertEquals(1L, saved.getTransactionId());

        assertEquals(
                TransactionStatus.COMPLETED,
                saved.getTransactionStatus()
        );

        assertEquals(
                TransactionType.DEPOSIT,
                saved.getTransactionType()
        );

        assertNull(saved.getSourceAccountId());
        assertEquals(21L, saved.getTargetAccountId());

        assertEquals(
                new BigDecimal("100.00"),
                saved.getAmount()
        );

        assertEquals("USD", saved.getCurrency());

        assertEquals(
                "Test transaction",
                saved.getDescription()
        );

        assertEquals(
                IDEMPOTENCY_KEY,
                saved.getIdempotencyKey()
        );

        assertNotNull(saved.getRequestHash());
        assertEquals(64, saved.getRequestHash().length());

        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        assertFalse(
                saved.getUpdatedAt()
                        .isBefore(saved.getCreatedAt())
        );

        assertNull(saved.getFailureReason());

        assertDoesNotThrow(() ->
                UUID.fromString(
                        saved.getTransactionReference()
                )
        );

        verify(transactionRepository)
                .findByIdempotencyKey(IDEMPOTENCY_KEY);

        verify(accountClient)
                .getAccountById(21L);

        verify(accountClient)
                .applyBalanceOperation(
                        eq(21L),
                        endsWith("-credit"),
                        anyString(),
                        eq("CREDIT"),
                        eq(new BigDecimal("100.00")),
                        contains("Deposit transaction")
                );

        /*
         * PENDING -> PROCESSING -> COMPLETED
         */
        verify(transactionRepository, times(3))
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldProcessWithdrawalAsCompleted() {

        when(transactionRepository.findByIdempotencyKey(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(transactionRepository.saveAndFlush(
                any(Transaction.class)
        )).thenAnswer(invocation -> {

            Transaction transaction =
                    invocation.getArgument(0);

            transaction.setTransactionId(2L);

            return transaction;
        });

        Transaction saved =
                transactionService.submitTransaction(
                        IDEMPOTENCY_KEY,
                        request(
                                TransactionType.WITHDRAWAL,
                                21L,
                                null
                        )
                );

        assertEquals(
                TransactionStatus.COMPLETED,
                saved.getTransactionStatus()
        );

        assertEquals(
                TransactionType.WITHDRAWAL,
                saved.getTransactionType()
        );

        assertEquals(21L, saved.getSourceAccountId());
        assertNull(saved.getTargetAccountId());
        assertNull(saved.getFailureReason());

        verify(accountClient)
                .getAccountById(21L);

        verify(accountClient)
                .applyBalanceOperation(
                        eq(21L),
                        endsWith("-debit"),
                        anyString(),
                        eq("DEBIT"),
                        eq(new BigDecimal("100.00")),
                        contains("Withdrawal transaction")
                );

        verify(transactionRepository, times(3))
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldProcessTransferAsCompleted() {

        when(transactionRepository.findByIdempotencyKey(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(accountClient.getAccountById(22L))
                .thenReturn(account(22L, "ACTIVE"));

        when(transactionRepository.saveAndFlush(
                any(Transaction.class)
        )).thenAnswer(invocation -> {

            Transaction transaction =
                    invocation.getArgument(0);

            transaction.setTransactionId(3L);

            return transaction;
        });

        Transaction saved =
                transactionService.submitTransaction(
                        IDEMPOTENCY_KEY,
                        request(
                                TransactionType.TRANSFER,
                                21L,
                                22L
                        )
                );

        assertEquals(3L, saved.getTransactionId());

        assertEquals(
                TransactionStatus.COMPLETED,
                saved.getTransactionStatus()
        );

        assertEquals(
                TransactionType.TRANSFER,
                saved.getTransactionType()
        );

        assertEquals(21L, saved.getSourceAccountId());
        assertEquals(22L, saved.getTargetAccountId());
        assertNull(saved.getFailureReason());

        verify(accountClient)
                .getAccountById(21L);

        verify(accountClient)
                .getAccountById(22L);

        verify(accountClient)
                .applyTransfer(
                        endsWith("-transfer"),
                        anyString(),
                        eq(21L),
                        eq(22L),
                        eq(new BigDecimal("100.00")),
                        contains("Transfer transaction")
                );

        verify(accountClient, never())
                .applyBalanceOperation(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(BigDecimal.class),
                        anyString()
                );

        verify(transactionRepository, times(3))
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldReturnExistingCompletedTransactionForSameRequest() {

        AtomicReference<Transaction> storedTransaction =
                new AtomicReference<>();

        when(transactionRepository.findByIdempotencyKey(
                IDEMPOTENCY_KEY
        ))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation ->
                        Optional.of(storedTransaction.get())
                );

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(transactionRepository.saveAndFlush(
                any(Transaction.class)
        )).thenAnswer(invocation -> {

            Transaction transaction =
                    invocation.getArgument(0);

            transaction.setTransactionId(1L);
            storedTransaction.set(transaction);

            return transaction;
        });

        TransactionRequest request =
                request(
                        TransactionType.DEPOSIT,
                        null,
                        21L
                );

        Transaction firstResult =
                transactionService.submitTransaction(
                        IDEMPOTENCY_KEY,
                        request
                );

        Transaction secondResult =
                transactionService.submitTransaction(
                        IDEMPOTENCY_KEY,
                        request
                );

        assertSame(firstResult, secondResult);

        assertEquals(
                TransactionStatus.COMPLETED,
                secondResult.getTransactionStatus()
        );

        assertEquals(
                firstResult.getTransactionId(),
                secondResult.getTransactionId()
        );

        assertEquals(
                firstResult.getTransactionReference(),
                secondResult.getTransactionReference()
        );

        verify(transactionRepository, times(2))
                .findByIdempotencyKey(IDEMPOTENCY_KEY);

        verify(accountClient, times(1))
                .getAccountById(21L);

        verify(accountClient, times(1))
                .applyBalanceOperation(
                        eq(21L),
                        endsWith("-credit"),
                        anyString(),
                        eq("CREDIT"),
                        eq(new BigDecimal("100.00")),
                        contains("Deposit transaction")
                );

        /*
         * The first request performs three saves:
         * PENDING -> PROCESSING -> COMPLETED.
         *
         * The repeated request performs no additional save.
         */
        verify(transactionRepository, times(3))
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldRejectSameKeyWithDifferentRequest() {

        AtomicReference<Transaction> storedTransaction =
                new AtomicReference<>();

        when(transactionRepository.findByIdempotencyKey(
                IDEMPOTENCY_KEY
        ))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation ->
                        Optional.of(storedTransaction.get())
                );

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(transactionRepository.saveAndFlush(
                any(Transaction.class)
        )).thenAnswer(invocation -> {

            Transaction transaction =
                    invocation.getArgument(0);

            transaction.setTransactionId(1L);
            storedTransaction.set(transaction);

            return transaction;
        });

        transactionService.submitTransaction(
                IDEMPOTENCY_KEY,
                request(
                        TransactionType.DEPOSIT,
                        null,
                        21L
                )
        );

        TransactionRequest differentRequest =
                new TransactionRequest(
                        TransactionType.DEPOSIT,
                        null,
                        21L,
                        new BigDecimal("200.00"),
                        "USD",
                        "Test transaction"
                );

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService
                                .submitTransaction(
                                        IDEMPOTENCY_KEY,
                                        differentRequest
                                )
                );

        assertEquals(
                ErrorCode.TRANSACTION_IDEMPOTENCY_CONFLICT,
                exception.getErrorCode()
        );

        verify(accountClient, times(1))
                .getAccountById(21L);

        verify(accountClient, times(1))
                .applyBalanceOperation(
                        eq(21L),
                        endsWith("-credit"),
                        anyString(),
                        eq("CREDIT"),
                        eq(new BigDecimal("100.00")),
                        contains("Deposit transaction")
                );

        verify(transactionRepository, times(3))
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldMarkTransactionFailedWhenBalanceOperationFails() {

        AtomicReference<Transaction> storedTransaction =
                new AtomicReference<>();

        when(transactionRepository.findByIdempotencyKey(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(transactionRepository.saveAndFlush(
                any(Transaction.class)
        )).thenAnswer(invocation -> {

            Transaction transaction =
                    invocation.getArgument(0);

            transaction.setTransactionId(3L);
            storedTransaction.set(transaction);

            return transaction;
        });

        doThrow(
                new TransactionBusinessException(
                        ErrorCode.INSUFFICIENT_FUNDS,
                        "Insufficient funds for account ID: 21"
                )
        ).when(accountClient)
                .applyBalanceOperation(
                        eq(21L),
                        anyString(),
                        anyString(),
                        eq("DEBIT"),
                        eq(new BigDecimal("100.00")),
                        anyString()
                );

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService
                                .submitTransaction(
                                        IDEMPOTENCY_KEY,
                                        request(
                                                TransactionType.WITHDRAWAL,
                                                21L,
                                                null
                                        )
                                )
                );

        assertEquals(
                ErrorCode.INSUFFICIENT_FUNDS,
                exception.getErrorCode()
        );

        Transaction failedTransaction =
                storedTransaction.get();

        assertNotNull(failedTransaction);

        assertEquals(
                TransactionStatus.FAILED,
                failedTransaction.getTransactionStatus()
        );

        assertEquals(
                "Insufficient funds for account ID: 21",
                failedTransaction.getFailureReason()
        );

        /*
         * PENDING -> PROCESSING -> FAILED
         */
        verify(transactionRepository, times(3))
                .saveAndFlush(failedTransaction);
    }

    @Test
    void shouldRejectBlankIdempotencyKey() {

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService
                                .submitTransaction(
                                        "   ",
                                        request(
                                                TransactionType.DEPOSIT,
                                                null,
                                                21L
                                        )
                                )
                );

        assertEquals(
                ErrorCode.INVALID_REQUEST,
                exception.getErrorCode()
        );

        assertEquals(
                "Idempotency-Key header is required",
                exception.getMessage()
        );

        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(accountClient);
    }

    @Test
    void shouldRejectIdempotencyKeyLongerThanOneHundredCharacters() {

        String longKey = "A".repeat(101);

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService
                                .submitTransaction(
                                        longKey,
                                        request(
                                                TransactionType.DEPOSIT,
                                                null,
                                                21L
                                        )
                                )
                );

        assertEquals(
                ErrorCode.INVALID_REQUEST,
                exception.getErrorCode()
        );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "must not exceed 100 characters"
                        )
        );

        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(accountClient);
    }

    @Test
    void shouldRejectNonActiveAccountWithoutSaving() {

        when(transactionRepository.findByIdempotencyKey(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "BLOCKED"));

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService
                                .submitTransaction(
                                        IDEMPOTENCY_KEY,
                                        request(
                                                TransactionType.DEPOSIT,
                                                null,
                                                21L
                                        )
                                )
                );

        assertEquals(
                ErrorCode.TRANSACTION_ACCOUNT_NOT_ACTIVE,
                exception.getErrorCode()
        );

        verify(transactionRepository)
                .findByIdempotencyKey(IDEMPOTENCY_KEY);

        verify(transactionRepository, never())
                .saveAndFlush(any(Transaction.class));

        verify(accountClient, never())
                .applyBalanceOperation(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(BigDecimal.class),
                        anyString()
                );
    }

    @Test
    void shouldRejectTransferWhenTargetIsNotActive() {

        when(transactionRepository.findByIdempotencyKey(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(accountClient.getAccountById(22L))
                .thenReturn(account(22L, "CLOSED"));

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService
                                .submitTransaction(
                                        IDEMPOTENCY_KEY,
                                        request(
                                                TransactionType.TRANSFER,
                                                21L,
                                                22L
                                        )
                                )
                );

        assertEquals(
                ErrorCode.TRANSACTION_ACCOUNT_NOT_ACTIVE,
                exception.getErrorCode()
        );

        verify(accountClient)
                .getAccountById(21L);

        verify(accountClient)
                .getAccountById(22L);

        verify(transactionRepository, never())
                .saveAndFlush(any(Transaction.class));

        verify(accountClient, never())
                .applyTransfer(
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyLong(),
                        any(BigDecimal.class),
                        anyString()
                );
    }

    @Test
    void shouldPropagateMissingAccountWithoutSaving() {

        when(transactionRepository.findByIdempotencyKey(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.empty());

        when(accountClient.getAccountById(999999L))
                .thenThrow(
                        new TransactionBusinessException(
                                ErrorCode.TRANSACTION_ACCOUNT_NOT_FOUND,
                                "Account not found with ID: 999999"
                        )
                );

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService
                                .submitTransaction(
                                        IDEMPOTENCY_KEY,
                                        request(
                                                TransactionType.DEPOSIT,
                                                null,
                                                999999L
                                        )
                                )
                );

        assertEquals(
                ErrorCode.TRANSACTION_ACCOUNT_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(transactionRepository, never())
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldPropagateDependencyFailureDuringValidationWithoutSaving() {

        when(transactionRepository.findByIdempotencyKey(
                IDEMPOTENCY_KEY
        )).thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenThrow(
                        new AccountServiceUnavailableException(
                                "Account Service is currently unavailable"
                        )
                );

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> transactionService
                        .submitTransaction(
                                IDEMPOTENCY_KEY,
                                request(
                                        TransactionType.DEPOSIT,
                                        null,
                                        21L
                                )
                        )
        );

        verify(transactionRepository, never())
                .saveAndFlush(any(Transaction.class));

        verify(accountClient, never())
                .applyBalanceOperation(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(BigDecimal.class),
                        anyString()
                );
    }

    @Test
    void shouldRetrieveTransactionByReference() {

        String reference =
                "27b3b07e-2176-4316-bf58-97248cd8fb74";

        Transaction transaction =
                Transaction.builder()
                        .transactionId(1L)
                        .transactionReference(reference)
                        .transactionType(
                                TransactionType.DEPOSIT
                        )
                        .targetAccountId(21L)
                        .amount(new BigDecimal("100.00"))
                        .currency("USD")
                        .transactionStatus(
                                TransactionStatus.COMPLETED
                        )
                        .description("Test transaction")
                        .build();

        when(transactionRepository
                .findByTransactionReference(reference))
                .thenReturn(Optional.of(transaction));

        Transaction result =
                transactionService
                        .getTransactionByReference(reference);

        assertSame(transaction, result);

        assertEquals(
                TransactionStatus.COMPLETED,
                result.getTransactionStatus()
        );

        verify(transactionRepository)
                .findByTransactionReference(reference);

        verifyNoMoreInteractions(transactionRepository);
        verifyNoInteractions(accountClient);
    }

    @Test
    void shouldRejectMissingTransactionReference() {

        String reference =
                "00000000-0000-0000-0000-000000000000";

        when(transactionRepository
                .findByTransactionReference(reference))
                .thenReturn(Optional.empty());

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService
                                .getTransactionByReference(
                                        reference
                                )
                );

        assertEquals(
                ErrorCode.TRANSACTION_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(transactionRepository)
                .findByTransactionReference(reference);

        verifyNoMoreInteractions(transactionRepository);
        verifyNoInteractions(accountClient);
    }

    private TransactionRequest request(
            TransactionType type,
            Long sourceId,
            Long targetId) {

        return new TransactionRequest(
                type,
                sourceId,
                targetId,
                new BigDecimal("100.00"),
                "USD",
                "Test transaction"
        );
    }

    private AccountClient.AccountLookupResponse account(
            Long accountId,
            String status) {

        return new AccountClient.AccountLookupResponse(
                accountId,
                "0000000021",
                5L,
                "SAVINGS",
                BigDecimal.ZERO,
                status,
                null,
                null
        );
    }
    @Test
    void shouldMarkDepositCompletedWhenReconciliationFindsLedgerOperation() {

        Transaction transaction =
                transaction(
                        TransactionStatus.RECONCILIATION_REQUIRED,
                        TransactionType.DEPOSIT
                );

        when(transactionRepository.findByTransactionReference(
                transaction.getTransactionReference()
        )).thenReturn(Optional.of(transaction));

        when(accountClient.balanceOperationExists(
                transaction.getTransactionReference() + "-credit"
        )).thenReturn(true);

        when(transactionRepository.saveAndFlush(transaction))
                .thenReturn(transaction);

        Transaction result =
                transactionService.reconcileTransaction(
                        transaction.getTransactionReference()
                );

        assertEquals(
                TransactionStatus.COMPLETED,
                result.getTransactionStatus()
        );

        assertNull(result.getFailureReason());

        verify(accountClient).balanceOperationExists(
                transaction.getTransactionReference() + "-credit"
        );

        verify(transactionRepository)
                .saveAndFlush(transaction);
    }

    @Test
    void shouldKeepTransactionInReconciliationWhenLedgerOperationIsMissing() {

        Transaction transaction =
                transaction(
                        TransactionStatus.RECONCILIATION_REQUIRED,
                        TransactionType.WITHDRAWAL
                );

        when(transactionRepository.findByTransactionReference(
                transaction.getTransactionReference()
        )).thenReturn(Optional.of(transaction));

        when(accountClient.balanceOperationExists(
                transaction.getTransactionReference() + "-debit"
        )).thenReturn(false);

        when(transactionRepository.saveAndFlush(transaction))
                .thenReturn(transaction);

        Transaction result =
                transactionService.reconcileTransaction(
                        transaction.getTransactionReference()
                );

        assertEquals(
                TransactionStatus.RECONCILIATION_REQUIRED,
                result.getTransactionStatus()
        );

        assertNotNull(result.getFailureReason());

        assertTrue(
                result.getFailureReason()
                        .contains("manual reconciliation")
        );

        verify(transactionRepository)
                .saveAndFlush(transaction);
    }

    @Test
    void shouldKeepTransactionInReconciliationWhenAccountServiceIsUnavailable() {

        Transaction transaction =
                transaction(
                        TransactionStatus.RECONCILIATION_REQUIRED,
                        TransactionType.DEPOSIT
                );

        when(transactionRepository.findByTransactionReference(
                transaction.getTransactionReference()
        )).thenReturn(Optional.of(transaction));

        when(accountClient.balanceOperationExists(
                transaction.getTransactionReference() + "-credit"
        )).thenThrow(
                new AccountServiceUnavailableException(
                        "Account Service is currently unavailable"
                )
        );

        when(transactionRepository.saveAndFlush(transaction))
                .thenReturn(transaction);

        Transaction result =
                transactionService.reconcileTransaction(
                        transaction.getTransactionReference()
                );

        assertEquals(
                TransactionStatus.RECONCILIATION_REQUIRED,
                result.getTransactionStatus()
        );

        assertEquals(
                "Account Service is currently unavailable",
                result.getFailureReason()
        );

        verify(transactionRepository)
                .saveAndFlush(transaction);
    }

    @Test
    void shouldReconcileTransferWhenBothLedgerOperationsExist() {

        Transaction transaction =
                transaction(
                        TransactionStatus.RECONCILIATION_REQUIRED,
                        TransactionType.TRANSFER
                );

        when(transactionRepository.findByTransactionReference(
                transaction.getTransactionReference()
        )).thenReturn(Optional.of(transaction));

        when(accountClient.transferOperationExists(
                transaction.getTransactionReference() + "-transfer"
        )).thenReturn(true);

        when(transactionRepository.saveAndFlush(transaction))
                .thenReturn(transaction);

        Transaction result =
                transactionService.reconcileTransaction(
                        transaction.getTransactionReference()
                );

        assertEquals(
                TransactionStatus.COMPLETED,
                result.getTransactionStatus()
        );

        verify(accountClient).transferOperationExists(
                transaction.getTransactionReference() + "-transfer"
        );

        verify(transactionRepository)
                .saveAndFlush(transaction);
    }

    @Test
    void shouldReturnCompletedTransactionWithoutCallingAccountService() {

        Transaction transaction =
                transaction(
                        TransactionStatus.COMPLETED,
                        TransactionType.DEPOSIT
                );

        when(transactionRepository.findByTransactionReference(
                transaction.getTransactionReference()
        )).thenReturn(Optional.of(transaction));

        Transaction result =
                transactionService.reconcileTransaction(
                        transaction.getTransactionReference()
                );

        assertSame(transaction, result);

        verifyNoInteractions(accountClient);

        verify(transactionRepository, never())
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldReturnFailedTransactionWithoutCallingAccountService() {

        Transaction transaction =
                transaction(
                        TransactionStatus.FAILED,
                        TransactionType.WITHDRAWAL
                );

        when(transactionRepository.findByTransactionReference(
                transaction.getTransactionReference()
        )).thenReturn(Optional.of(transaction));

        Transaction result =
                transactionService.reconcileTransaction(
                        transaction.getTransactionReference()
                );

        assertSame(transaction, result);

        verifyNoInteractions(accountClient);

        verify(transactionRepository, never())
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldRejectPendingTransactionReconciliation() {

        Transaction transaction =
                transaction(
                        TransactionStatus.PENDING,
                        TransactionType.DEPOSIT
                );

        when(transactionRepository.findByTransactionReference(
                transaction.getTransactionReference()
        )).thenReturn(Optional.of(transaction));

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService
                                .reconcileTransaction(
                                        transaction.getTransactionReference()
                                )
                );

        assertEquals(
                ErrorCode.INVALID_REQUEST,
                exception.getErrorCode()
        );

        assertEquals(
                "A PENDING transaction cannot be reconciled",
                exception.getMessage()
        );

        verifyNoInteractions(accountClient);

        verify(transactionRepository, never())
                .saveAndFlush(any(Transaction.class));
    }

    private Transaction transaction(
            TransactionStatus status,
            TransactionType type) {

        String reference =
                "27b3b07e-2176-4316-bf58-97248cd8fb74";

        return Transaction.builder()
                .transactionId(10L)
                .transactionReference(reference)
                .idempotencyKey("reconciliation-test-001")
                .requestHash("a".repeat(64))
                .transactionType(type)
                .sourceAccountId(
                        type == TransactionType.DEPOSIT
                                ? null
                                : 21L
                )
                .targetAccountId(
                        type == TransactionType.WITHDRAWAL
                                ? null
                                : 22L
                )
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .transactionStatus(status)
                .description("Reconciliation test")
                .failureReason(
                        status == TransactionStatus.RECONCILIATION_REQUIRED
                                ? "Account Service response was uncertain"
                                : null
                )
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
    }
}
