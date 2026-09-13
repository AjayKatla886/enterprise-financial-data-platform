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
import static org.mockito.ArgumentMatchers.any;
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
    void shouldRecordDepositAsPending() {

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    transaction.setTransactionId(1L);
                    return transaction;
                });

        Transaction saved = transactionService.submitTransaction(
                IDEMPOTENCY_KEY,
                request(TransactionType.DEPOSIT, null, 21L)
        );

        assertEquals(1L, saved.getTransactionId());
        assertEquals(
                TransactionStatus.PENDING,
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
        assertEquals("Test transaction", saved.getDescription());

        assertEquals(
                IDEMPOTENCY_KEY,
                saved.getIdempotencyKey()
        );

        assertNotNull(saved.getRequestHash());
        assertEquals(64, saved.getRequestHash().length());

        assertNotNull(saved.getCreatedAt());
        assertEquals(
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );

        assertDoesNotThrow(() ->
                UUID.fromString(saved.getTransactionReference())
        );

        verify(transactionRepository)
                .findByIdempotencyKey(IDEMPOTENCY_KEY);

        verify(accountClient)
                .getAccountById(21L);

        verify(transactionRepository)
                .saveAndFlush(saved);
    }

    @Test
    void shouldValidateBothAccountsForTransfer() {

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(accountClient.getAccountById(22L))
                .thenReturn(account(22L, "ACTIVE"));

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = transactionService.submitTransaction(
                IDEMPOTENCY_KEY,
                request(TransactionType.TRANSFER, 21L, 22L)
        );

        assertEquals(21L, saved.getSourceAccountId());
        assertEquals(22L, saved.getTargetAccountId());

        assertEquals(
                TransactionStatus.PENDING,
                saved.getTransactionStatus()
        );

        assertEquals(
                IDEMPOTENCY_KEY,
                saved.getIdempotencyKey()
        );

        assertNotNull(saved.getRequestHash());

        verify(accountClient).getAccountById(21L);
        verify(accountClient).getAccountById(22L);

        verify(transactionRepository)
                .saveAndFlush(saved);
    }

    @Test
    void shouldReturnExistingTransactionForSameKeyAndRequest() {

        AtomicReference<Transaction> storedTransaction =
                new AtomicReference<>();

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation ->
                        Optional.of(storedTransaction.get())
                );

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    transaction.setTransactionId(1L);
                    storedTransaction.set(transaction);
                    return transaction;
                });

        TransactionRequest request =
                request(TransactionType.DEPOSIT, null, 21L);

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
                firstResult.getTransactionId(),
                secondResult.getTransactionId()
        );

        assertEquals(
                firstResult.getTransactionReference(),
                secondResult.getTransactionReference()
        );

        verify(transactionRepository, times(2))
                .findByIdempotencyKey(IDEMPOTENCY_KEY);

        /*
         * The replay must not validate the account again.
         */
        verify(accountClient, times(1))
                .getAccountById(21L);

        /*
         * Only one transaction must be inserted.
         */
        verify(transactionRepository, times(1))
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldRejectSameKeyWithDifferentRequest() {

        AtomicReference<Transaction> storedTransaction =
                new AtomicReference<>();

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation ->
                        Optional.of(storedTransaction.get())
                );

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    transaction.setTransactionId(1L);
                    storedTransaction.set(transaction);
                    return transaction;
                });

        transactionService.submitTransaction(
                IDEMPOTENCY_KEY,
                request(TransactionType.DEPOSIT, null, 21L)
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
                        () -> transactionService.submitTransaction(
                                IDEMPOTENCY_KEY,
                                differentRequest
                        )
                );

        assertEquals(
                ErrorCode.TRANSACTION_IDEMPOTENCY_CONFLICT,
                exception.getErrorCode()
        );

        /*
         * Account validation and database insert happened only
         * during the first request.
         */
        verify(accountClient, times(1))
                .getAccountById(21L);

        verify(transactionRepository, times(1))
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldRejectBlankIdempotencyKey() {

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService.submitTransaction(
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
                        () -> transactionService.submitTransaction(
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
                        .contains("must not exceed 100 characters")
        );

        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(accountClient);
    }

    @Test
    void shouldRejectNonActiveAccountWithoutSaving() {

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "BLOCKED"));

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService.submitTransaction(
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
    }

    @Test
    void shouldRejectTransferWhenTargetIsNotActive() {

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenReturn(account(21L, "ACTIVE"));

        when(accountClient.getAccountById(22L))
                .thenReturn(account(22L, "CLOSED"));

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService.submitTransaction(
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

        verify(accountClient).getAccountById(21L);
        verify(accountClient).getAccountById(22L);

        verify(transactionRepository, never())
                .saveAndFlush(any(Transaction.class));
    }

    @Test
    void shouldPropagateMissingAccountWithoutSaving() {

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(accountClient.getAccountById(999999L))
                .thenThrow(new TransactionBusinessException(
                        ErrorCode.TRANSACTION_ACCOUNT_NOT_FOUND,
                        "Account not found with ID: 999999"
                ));

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService.submitTransaction(
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
    void shouldPropagateDependencyFailureWithoutSaving() {

        when(transactionRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        when(accountClient.getAccountById(21L))
                .thenThrow(new AccountServiceUnavailableException(
                        "Account Service is currently unavailable"
                ));

        assertThrows(
                AccountServiceUnavailableException.class,
                () -> transactionService.submitTransaction(
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
    }

    @Test
    void shouldRetrieveTransactionByReference() {

        String reference =
                "27b3b07e-2176-4316-bf58-97248cd8fb74";

        Transaction transaction = Transaction.builder()
                .transactionId(1L)
                .transactionReference(reference)
                .transactionType(TransactionType.DEPOSIT)
                .targetAccountId(21L)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .transactionStatus(TransactionStatus.PENDING)
                .description("Test transaction")
                .build();

        when(transactionRepository.findByTransactionReference(reference))
                .thenReturn(Optional.of(transaction));

        Transaction result =
                transactionService.getTransactionByReference(
                        reference
                );

        assertSame(transaction, result);

        assertEquals(
                TransactionStatus.PENDING,
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

        when(transactionRepository.findByTransactionReference(reference))
                .thenReturn(Optional.empty());

        TransactionBusinessException exception =
                assertThrows(
                        TransactionBusinessException.class,
                        () -> transactionService
                                .getTransactionByReference(reference)
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
}