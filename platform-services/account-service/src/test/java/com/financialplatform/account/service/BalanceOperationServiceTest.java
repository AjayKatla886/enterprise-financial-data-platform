package com.financialplatform.account.service;

import com.financialplatform.account.dto.BalanceOperationRequest;
import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.AccountStatus;
import com.financialplatform.account.entity.AccountType;
import com.financialplatform.account.entity.BalanceOperation;
import com.financialplatform.account.entity.BalanceOperationType;
import com.financialplatform.account.exception.AccountBusinessException;
import com.financialplatform.account.repository.AccountRepository;
import com.financialplatform.account.repository.BalanceOperationRepository;
import com.financialplatform.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceOperationServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceOperationRepository balanceOperationRepository;

    @InjectMocks
    private BalanceOperationService balanceOperationService;

    @Test
    void shouldDebitActiveAccount() {

        Account account = buildAccount(
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByOperationReference(
                "txn-1001-debit"
        )).thenReturn(Optional.empty());

        when(balanceOperationRepository.save(any(BalanceOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BalanceOperation result =
                balanceOperationService.applyBalanceOperation(
                        21L,
                        "txn-1001-debit",
                        request(
                                BalanceOperationType.DEBIT,
                                new BigDecimal("100.00")
                        )
                );

        assertEquals(
                new BigDecimal("400.00"),
                account.getBalance()
        );

        assertEquals(
                new BigDecimal("500.00"),
                result.getBalanceBefore()
        );

        assertEquals(
                new BigDecimal("400.00"),
                result.getBalanceAfter()
        );

        assertEquals(
                BalanceOperationType.DEBIT,
                result.getOperationType()
        );

        assertNotNull(result.getRequestHash());
        assertEquals(64, result.getRequestHash().length());

        verify(accountRepository).findByIdForUpdate(21L);
        verify(balanceOperationRepository)
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldCreditActiveAccount() {

        Account account = buildAccount(
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByOperationReference(
                "txn-1002-credit"
        )).thenReturn(Optional.empty());

        when(balanceOperationRepository.save(any(BalanceOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BalanceOperation result =
                balanceOperationService.applyBalanceOperation(
                        21L,
                        "txn-1002-credit",
                        request(
                                BalanceOperationType.CREDIT,
                                new BigDecimal("150.00")
                        )
                );

        assertEquals(
                new BigDecimal("650.00"),
                account.getBalance()
        );

        assertEquals(
                new BigDecimal("500.00"),
                result.getBalanceBefore()
        );

        assertEquals(
                new BigDecimal("650.00"),
                result.getBalanceAfter()
        );

        assertEquals(
                BalanceOperationType.CREDIT,
                result.getOperationType()
        );

        verify(balanceOperationRepository)
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldRejectDebitWhenBalanceIsInsufficient() {

        Account account = buildAccount(
                new BigDecimal("50.00"),
                AccountStatus.ACTIVE
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByOperationReference(
                "txn-1003-debit"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyBalanceOperation(
                        21L,
                        "txn-1003-debit",
                        request(
                                BalanceOperationType.DEBIT,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.INSUFFICIENT_FUNDS,
                exception.getErrorCode()
        );

        assertEquals(
                new BigDecimal("50.00"),
                account.getBalance()
        );

        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldRejectOperationForInactiveAccount() {

        Account account = buildAccount(
                new BigDecimal("500.00"),
                AccountStatus.INACTIVE
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByOperationReference(
                "txn-1004-debit"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyBalanceOperation(
                        21L,
                        "txn-1004-debit",
                        request(
                                BalanceOperationType.DEBIT,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.INVALID_ACCOUNT_STATE,
                exception.getErrorCode()
        );

        assertEquals(
                new BigDecimal("500.00"),
                account.getBalance()
        );

        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldReturnExistingOperationForIdempotentRetry() {

        Account account = buildAccount(
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE
        );

        AtomicReference<BalanceOperation> storedOperation =
                new AtomicReference<>();

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByOperationReference(
                "txn-1005-debit"
        )).thenAnswer(invocation ->
                Optional.ofNullable(storedOperation.get())
        );

        when(balanceOperationRepository.save(any(BalanceOperation.class)))
                .thenAnswer(invocation -> {
                    BalanceOperation operation =
                            invocation.getArgument(0);

                    storedOperation.set(operation);
                    return operation;
                });

        BalanceOperationRequest request = request(
                BalanceOperationType.DEBIT,
                new BigDecimal("100.00")
        );

        BalanceOperation firstResult =
                balanceOperationService.applyBalanceOperation(
                        21L,
                        "txn-1005-debit",
                        request
                );

        BalanceOperation secondResult =
                balanceOperationService.applyBalanceOperation(
                        21L,
                        "txn-1005-debit",
                        request
                );

        assertSame(firstResult, secondResult);

        assertEquals(
                new BigDecimal("400.00"),
                account.getBalance()
        );

        verify(balanceOperationRepository, times(1))
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldRejectIdempotencyKeyUsedWithDifferentRequest() {

        Account account = buildAccount(
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE
        );

        BalanceOperation existingOperation =
                BalanceOperation.builder()
                        .operationReference("txn-1006-debit")
                        .transactionReference(
                                "550e8400-e29b-41d4-a716-446655440000"
                        )
                        .accountId(21L)
                        .operationType(BalanceOperationType.DEBIT)
                        .amount(new BigDecimal("50.00"))
                        .balanceBefore(new BigDecimal("500.00"))
                        .balanceAfter(new BigDecimal("450.00"))
                        .requestHash("different-request-hash")
                        .description("Test balance operation")
                        .createdAt(LocalDateTime.now())
                        .build();

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByOperationReference(
                "txn-1006-debit"
        )).thenReturn(Optional.of(existingOperation));

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyBalanceOperation(
                        21L,
                        "txn-1006-debit",
                        request(
                                BalanceOperationType.DEBIT,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.BALANCE_OPERATION_IDEMPOTENCY_CONFLICT,
                exception.getErrorCode()
        );

        assertEquals(
                new BigDecimal("500.00"),
                account.getBalance()
        );

        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldRejectMissingAccount() {

        when(accountRepository.findByIdForUpdate(999999L))
                .thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyBalanceOperation(
                        999999L,
                        "txn-missing-account",
                        request(
                                BalanceOperationType.DEBIT,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.ACCOUNT_NOT_FOUND,
                exception.getErrorCode()
        );

        verifyNoInteractions(balanceOperationRepository);
    }

    @Test
    void shouldRejectDirectReversalOperation() {

        Account account = buildAccount(
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByOperationReference(
                "txn-1007-reversal"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyBalanceOperation(
                        21L,
                        "txn-1007-reversal",
                        request(
                                BalanceOperationType.REVERSAL,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.INVALID_REQUEST,
                exception.getErrorCode()
        );

        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    private Account buildAccount(
            BigDecimal balance,
            AccountStatus status) {

        LocalDateTime now = LocalDateTime.now();

        return Account.builder()
                .accountId(21L)
                .accountNumber("0000000021")
                .customerId(5L)
                .accountType(AccountType.SAVINGS)
                .balance(balance)
                .accountStatus(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private BalanceOperationRequest request(
            BalanceOperationType operationType,
            BigDecimal amount) {

        return new BalanceOperationRequest(
                "550e8400-e29b-41d4-a716-446655440000",
                operationType,
                amount,
                "Test balance operation"
        );
    }
}