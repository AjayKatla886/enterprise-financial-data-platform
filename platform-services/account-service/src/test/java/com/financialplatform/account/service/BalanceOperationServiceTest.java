package com.financialplatform.account.service;

import com.financialplatform.account.dto.AccountTransferRequest;
import com.financialplatform.account.dto.AccountTransferResponse;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
                "debit-operation-001"
        )).thenReturn(Optional.empty());

        when(balanceOperationRepository.save(
                any(BalanceOperation.class)
        )).thenAnswer(invocation ->
                invocation.<BalanceOperation>getArgument(0)
        );

        BalanceOperation result =
                balanceOperationService.applyBalanceOperation(
                        21L,
                        "debit-operation-001",
                        balanceRequest(
                                BalanceOperationType.DEBIT,
                                new BigDecimal("100.00")
                        )
                );

        assertMoneyEquals("400.00", account.getBalance());
        assertEquals(
                BalanceOperationType.DEBIT,
                result.getOperationType()
        );
        assertMoneyEquals("500.00", result.getBalanceBefore());
        assertMoneyEquals("400.00", result.getBalanceAfter());
        assertNotNull(result.getRequestHash());
        assertEquals(64, result.getRequestHash().length());

        verify(accountRepository).findByIdForUpdate(21L);
        verify(balanceOperationRepository)
                .findByOperationReference("debit-operation-001");
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
                "credit-operation-001"
        )).thenReturn(Optional.empty());

        when(balanceOperationRepository.save(
                any(BalanceOperation.class)
        )).thenAnswer(invocation ->
                invocation.<BalanceOperation>getArgument(0)
        );

        BalanceOperation result =
                balanceOperationService.applyBalanceOperation(
                        21L,
                        "credit-operation-001",
                        balanceRequest(
                                BalanceOperationType.CREDIT,
                                new BigDecimal("100.00")
                        )
                );

        assertMoneyEquals("600.00", account.getBalance());
        assertEquals(
                BalanceOperationType.CREDIT,
                result.getOperationType()
        );
        assertMoneyEquals("500.00", result.getBalanceBefore());
        assertMoneyEquals("600.00", result.getBalanceAfter());

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
                "debit-operation-002"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyBalanceOperation(
                        21L,
                        "debit-operation-002",
                        balanceRequest(
                                BalanceOperationType.DEBIT,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.INSUFFICIENT_FUNDS,
                exception.getErrorCode()
        );

        assertMoneyEquals("50.00", account.getBalance());

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
                "inactive-operation-001"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyBalanceOperation(
                        21L,
                        "inactive-operation-001",
                        balanceRequest(
                                BalanceOperationType.DEBIT,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.INVALID_ACCOUNT_STATE,
                exception.getErrorCode()
        );

        assertMoneyEquals("500.00", account.getBalance());

        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldReturnExistingOperationForIdempotentRetry() {

        BalanceOperationRequest request =
                balanceRequest(
                        BalanceOperationType.DEBIT,
                        new BigDecimal("100.00")
                );

        Account firstAccount = buildAccount(
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(firstAccount));

        when(balanceOperationRepository.findByOperationReference(
                "retry-operation-001"
        )).thenReturn(Optional.empty());

        when(balanceOperationRepository.save(
                any(BalanceOperation.class)
        )).thenAnswer(invocation ->
                invocation.<BalanceOperation>getArgument(0)
        );

        BalanceOperation firstResult =
                balanceOperationService.applyBalanceOperation(
                        21L,
                        "retry-operation-001",
                        request
                );

        BalanceOperation existingOperation =
                createExistingOperation(
                        "retry-operation-001",
                        21L,
                        BalanceOperationType.DEBIT,
                        new BigDecimal("100.00"),
                        new BigDecimal("500.00"),
                        new BigDecimal("400.00"),
                        firstResult.getRequestHash()
                );

        reset(
                accountRepository,
                balanceOperationRepository
        );

        Account retryAccount = buildAccount(
                new BigDecimal("400.00"),
                AccountStatus.ACTIVE
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(retryAccount));

        when(balanceOperationRepository.findByOperationReference(
                "retry-operation-001"
        )).thenReturn(Optional.of(existingOperation));

        BalanceOperation result =
                balanceOperationService.applyBalanceOperation(
                        21L,
                        "retry-operation-001",
                        request
                );

        assertSame(existingOperation, result);
        assertMoneyEquals("400.00", retryAccount.getBalance());

        verify(accountRepository).findByIdForUpdate(21L);
        verify(balanceOperationRepository)
                .findByOperationReference("retry-operation-001");
        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldRejectIdempotencyKeyUsedWithDifferentRequest() {

        Account account = buildAccount(
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE
        );

        BalanceOperation existingOperation =
                createExistingOperation(
                        "conflict-operation-001",
                        21L,
                        BalanceOperationType.DEBIT,
                        new BigDecimal("50.00"),
                        new BigDecimal("500.00"),
                        new BigDecimal("450.00")
                );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByOperationReference(
                "conflict-operation-001"
        )).thenReturn(Optional.of(existingOperation));

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyBalanceOperation(
                        21L,
                        "conflict-operation-001",
                        balanceRequest(
                                BalanceOperationType.DEBIT,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.BALANCE_OPERATION_IDEMPOTENCY_CONFLICT,
                exception.getErrorCode()
        );

        assertMoneyEquals("500.00", account.getBalance());

        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldRejectMissingAccount() {

        when(accountRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyBalanceOperation(
                        999L,
                        "missing-account-operation",
                        balanceRequest(
                                BalanceOperationType.DEBIT,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.ACCOUNT_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(accountRepository).findByIdForUpdate(999L);
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
                "reversal-operation-001"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyBalanceOperation(
                        21L,
                        "reversal-operation-001",
                        balanceRequest(
                                BalanceOperationType.REVERSAL,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.INVALID_REQUEST,
                exception.getErrorCode()
        );

        assertMoneyEquals("500.00", account.getBalance());

        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldTransferMoneyAtomically() {

        Account sourceAccount = buildAccount(
                21L,
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE
        );

        Account targetAccount = buildAccount(
                22L,
                new BigDecimal("200.00"),
                AccountStatus.ACTIVE
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(sourceAccount));

        when(accountRepository.findByIdForUpdate(22L))
                .thenReturn(Optional.of(targetAccount));

        when(balanceOperationRepository.findByOperationReference(
                "transfer-operation-001-debit"
        )).thenReturn(Optional.empty());

        when(balanceOperationRepository.findByOperationReference(
                "transfer-operation-001-credit"
        )).thenReturn(Optional.empty());

        when(balanceOperationRepository.save(
                any(BalanceOperation.class)
        )).thenAnswer(invocation ->
                invocation.<BalanceOperation>getArgument(0)
        );

        AccountTransferResponse response =
                balanceOperationService.applyTransfer(
                        "transfer-operation-001",
                        transferRequest(
                                21L,
                                22L,
                                new BigDecimal("100.00")
                        )
                );

        assertMoneyEquals("400.00", sourceAccount.getBalance());
        assertMoneyEquals("300.00", targetAccount.getBalance());

        assertEquals(
                "550e8400-e29b-41d4-a716-446655440010",
                response.transactionReference()
        );

        assertEquals(
                "DEBIT",
                response.debitOperation().operationType()
        );

        assertEquals(
                "CREDIT",
                response.creditOperation().operationType()
        );

        assertMoneyEquals(
                "500.00",
                response.debitOperation().balanceBefore()
        );

        assertMoneyEquals(
                "400.00",
                response.debitOperation().balanceAfter()
        );

        assertMoneyEquals(
                "200.00",
                response.creditOperation().balanceBefore()
        );

        assertMoneyEquals(
                "300.00",
                response.creditOperation().balanceAfter()
        );

        verify(balanceOperationRepository, times(2))
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldLockTransferAccountsInAscendingOrder() {

        Account sourceAccount = buildAccount(
                22L,
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE
        );

        Account targetAccount = buildAccount(
                21L,
                new BigDecimal("200.00"),
                AccountStatus.ACTIVE
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(targetAccount));

        when(accountRepository.findByIdForUpdate(22L))
                .thenReturn(Optional.of(sourceAccount));

        when(balanceOperationRepository.findByOperationReference(
                anyString()
        )).thenReturn(Optional.empty());

        when(balanceOperationRepository.save(
                any(BalanceOperation.class)
        )).thenAnswer(invocation ->
                invocation.<BalanceOperation>getArgument(0)
        );

        balanceOperationService.applyTransfer(
                "transfer-lock-order",
                transferRequest(
                        22L,
                        21L,
                        new BigDecimal("100.00")
                )
        );

        InOrder lockOrder = inOrder(accountRepository);

        lockOrder.verify(accountRepository)
                .findByIdForUpdate(21L);

        lockOrder.verify(accountRepository)
                .findByIdForUpdate(22L);

        assertMoneyEquals("400.00", sourceAccount.getBalance());
        assertMoneyEquals("300.00", targetAccount.getBalance());
    }

    @Test
    void shouldRejectTransferWhenSourceHasInsufficientFunds() {

        Account sourceAccount = buildAccount(
                21L,
                new BigDecimal("50.00"),
                AccountStatus.ACTIVE
        );

        Account targetAccount = buildAccount(
                22L,
                new BigDecimal("200.00"),
                AccountStatus.ACTIVE
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(sourceAccount));

        when(accountRepository.findByIdForUpdate(22L))
                .thenReturn(Optional.of(targetAccount));

        when(balanceOperationRepository.findByOperationReference(
                "insufficient-transfer-debit"
        )).thenReturn(Optional.empty());

        when(balanceOperationRepository.findByOperationReference(
                "insufficient-transfer-credit"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyTransfer(
                        "insufficient-transfer",
                        transferRequest(
                                21L,
                                22L,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.INSUFFICIENT_FUNDS,
                exception.getErrorCode()
        );

        assertMoneyEquals("50.00", sourceAccount.getBalance());
        assertMoneyEquals("200.00", targetAccount.getBalance());

        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldRejectTransferWhenTargetIsInactive() {

        Account sourceAccount = buildAccount(
                21L,
                new BigDecimal("500.00"),
                AccountStatus.ACTIVE
        );

        Account targetAccount = buildAccount(
                22L,
                new BigDecimal("200.00"),
                AccountStatus.BLOCKED
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(sourceAccount));

        when(accountRepository.findByIdForUpdate(22L))
                .thenReturn(Optional.of(targetAccount));

        when(balanceOperationRepository.findByOperationReference(
                "inactive-transfer-debit"
        )).thenReturn(Optional.empty());

        when(balanceOperationRepository.findByOperationReference(
                "inactive-transfer-credit"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyTransfer(
                        "inactive-transfer",
                        transferRequest(
                                21L,
                                22L,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.INVALID_ACCOUNT_STATE,
                exception.getErrorCode()
        );

        assertMoneyEquals("500.00", sourceAccount.getBalance());
        assertMoneyEquals("200.00", targetAccount.getBalance());

        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    @Test
    void shouldRejectIncompleteTransferLedgerState() {

        Account sourceAccount = buildAccount(
                21L,
                new BigDecimal("400.00"),
                AccountStatus.ACTIVE
        );

        Account targetAccount = buildAccount(
                22L,
                new BigDecimal("300.00"),
                AccountStatus.ACTIVE
        );

        BalanceOperation existingDebit =
                createExistingOperation(
                        "partial-transfer-debit",
                        21L,
                        BalanceOperationType.DEBIT,
                        new BigDecimal("100.00"),
                        new BigDecimal("500.00"),
                        new BigDecimal("400.00")
                );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(sourceAccount));

        when(accountRepository.findByIdForUpdate(22L))
                .thenReturn(Optional.of(targetAccount));

        when(balanceOperationRepository.findByOperationReference(
                "partial-transfer-debit"
        )).thenReturn(Optional.of(existingDebit));

        when(balanceOperationRepository.findByOperationReference(
                "partial-transfer-credit"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyTransfer(
                        "partial-transfer",
                        transferRequest(
                                21L,
                                22L,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.BALANCE_OPERATION_IDEMPOTENCY_CONFLICT,
                exception.getErrorCode()
        );

        assertMoneyEquals("400.00", sourceAccount.getBalance());
        assertMoneyEquals("300.00", targetAccount.getBalance());

        verify(balanceOperationRepository, never())
                .save(any(BalanceOperation.class));
    }

    private BalanceOperationRequest balanceRequest(
            BalanceOperationType operationType,
            BigDecimal amount) {

        return new BalanceOperationRequest(
                "550e8400-e29b-41d4-a716-446655440000",
                operationType,
                amount,
                "Test balance operation"
        );
    }

    private AccountTransferRequest transferRequest(
            Long sourceAccountId,
            Long targetAccountId,
            BigDecimal amount) {

        return new AccountTransferRequest(
                sourceAccountId,
                targetAccountId,
                "550e8400-e29b-41d4-a716-446655440010",
                amount,
                "Test atomic transfer"
        );
    }

    private Account buildAccount(
            BigDecimal balance,
            AccountStatus status) {

        return buildAccount(
                21L,
                balance,
                status
        );
    }

    private Account buildAccount(
            Long accountId,
            BigDecimal balance,
            AccountStatus status) {

        LocalDateTime now = LocalDateTime.now();

        return Account.builder()
                .accountId(accountId)
                .accountNumber(String.format("%010d", accountId))
                .customerId(5L)
                .accountType(AccountType.SAVINGS)
                .balance(balance)
                .accountStatus(status)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private BalanceOperation createExistingOperation(
            String operationReference,
            Long accountId,
            BalanceOperationType operationType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter) {

        return createExistingOperation(
                operationReference,
                accountId,
                operationType,
                amount,
                balanceBefore,
                balanceAfter,
                "existing-request-hash"
        );
    }

    private BalanceOperation createExistingOperation(
            String operationReference,
            Long accountId,
            BalanceOperationType operationType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String requestHash) {

        return BalanceOperation.builder()
                .balanceOperationId(1L)
                .operationReference(operationReference)
                .transactionReference(
                        "550e8400-e29b-41d4-a716-446655440000"
                )
                .accountId(accountId)
                .operationType(operationType)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .requestHash(requestHash)
                .description("Test balance operation")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void assertMoneyEquals(
            String expected,
            BigDecimal actual) {

        assertNotNull(actual);

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }
    @Test
    void shouldRejectTransferBetweenSameAccount() {

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> balanceOperationService.applyTransfer(
                        "same-account-transfer",
                        transferRequest(
                                21L,
                                21L,
                                new BigDecimal("100.00")
                        )
                )
        );

        assertEquals(
                ErrorCode.INVALID_REQUEST,
                exception.getErrorCode()
        );

        assertEquals(
                "Source and target accounts must be different",
                exception.getMessage()
        );

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(balanceOperationRepository);
    }
}