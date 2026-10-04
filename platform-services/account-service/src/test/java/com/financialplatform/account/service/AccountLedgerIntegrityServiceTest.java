package com.financialplatform.account.service;

import com.financialplatform.account.dto.LedgerIntegrityResponse;
import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.AccountStatus;
import com.financialplatform.account.entity.AccountType;
import com.financialplatform.account.entity.BalanceOperation;
import com.financialplatform.account.entity.BalanceOperationType;
import com.financialplatform.account.entity.LedgerIntegrityStatus;
import com.financialplatform.account.exception.AccountBusinessException;
import com.financialplatform.account.repository.AccountRepository;
import com.financialplatform.account.repository.BalanceOperationRepository;
import com.financialplatform.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLedgerIntegrityServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceOperationRepository
            balanceOperationRepository;

    @InjectMocks
    private AccountLedgerIntegrityService
            accountLedgerIntegrityService;

    @Test
    void shouldVerifyValidLedger() {

        Account account =
                buildAccount("150.00");

        BalanceOperation credit =
                operation(
                        1L,
                        "credit-001",
                        BalanceOperationType.CREDIT,
                        "100.00",
                        "0.00",
                        "100.00",
                        LocalDateTime.of(
                                2026,
                                9,
                                1,
                                10,
                                0
                        )
                );

        BalanceOperation secondCredit =
                operation(
                        2L,
                        "credit-002",
                        BalanceOperationType.CREDIT,
                        "50.00",
                        "100.00",
                        "150.00",
                        LocalDateTime.of(
                                2026,
                                9,
                                2,
                                10,
                                0
                        )
                );

        Pageable pageable =
                auditPageable();

        when(accountRepository.findById(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByAccountId(
                21L,
                pageable
        )).thenReturn(
                new PageImpl<>(
                        List.of(
                                credit,
                                secondCredit
                        ),
                        pageable,
                        2
                )
        );

        LedgerIntegrityResponse result =
                accountLedgerIntegrityService
                        .checkLedgerIntegrity(21L);

        assertEquals(
                LedgerIntegrityStatus.VALID,
                result.integrityStatus()
        );

        assertTrue(result.balanceMatches());
        assertTrue(result.ledgerChainValid());
        assertTrue(result.operationCalculationsValid());
        assertTrue(result.issues().isEmpty());
        assertEquals(2L, result.operationCount());

        assertMoneyEquals(
                "150.00",
                result.storedAccountBalance()
        );

        assertMoneyEquals(
                "150.00",
                result.ledgerClosingBalance()
        );

        assertNotNull(result.checkedAt());

        verify(accountRepository).findById(21L);

        verify(balanceOperationRepository)
                .findByAccountId(
                        21L,
                        pageable
                );
    }

    @Test
    void shouldDetectLedgerChainBreak() {

        Account account =
                buildAccount("80.00");

        BalanceOperation credit =
                operation(
                        1L,
                        "credit-chain-001",
                        BalanceOperationType.CREDIT,
                        "100.00",
                        "0.00",
                        "100.00",
                        LocalDateTime.of(
                                2026,
                                9,
                                1,
                                10,
                                0
                        )
                );

        /*
         * This debit is mathematically correct by itself:
         * 90 - 10 = 80.
         *
         * However, it starts at 90 while the previous operation
         * ended at 100, producing a ledger-chain break.
         */
        BalanceOperation debit =
                operation(
                        2L,
                        "debit-chain-001",
                        BalanceOperationType.DEBIT,
                        "10.00",
                        "90.00",
                        "80.00",
                        LocalDateTime.of(
                                2026,
                                9,
                                2,
                                10,
                                0
                        )
                );

        Pageable pageable =
                auditPageable();

        when(accountRepository.findById(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByAccountId(
                21L,
                pageable
        )).thenReturn(
                new PageImpl<>(
                        List.of(
                                credit,
                                debit
                        ),
                        pageable,
                        2
                )
        );

        LedgerIntegrityResponse result =
                accountLedgerIntegrityService
                        .checkLedgerIntegrity(21L);

        assertEquals(
                LedgerIntegrityStatus.INVALID,
                result.integrityStatus()
        );

        assertTrue(result.balanceMatches());
        assertFalse(result.ledgerChainValid());
        assertTrue(result.operationCalculationsValid());

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(issue ->
                                issue.contains(
                                        "Ledger chain break"
                                )
                        )
        );
    }

    @Test
    void shouldDetectInvalidOperationCalculation() {

        Account account =
                buildAccount("90.00");

        /*
         * Invalid calculation:
         * 0 + 100 should equal 100, not 90.
         */
        BalanceOperation invalidCredit =
                operation(
                        1L,
                        "invalid-credit-001",
                        BalanceOperationType.CREDIT,
                        "100.00",
                        "0.00",
                        "90.00",
                        LocalDateTime.of(
                                2026,
                                9,
                                1,
                                10,
                                0
                        )
                );

        Pageable pageable =
                auditPageable();

        when(accountRepository.findById(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByAccountId(
                21L,
                pageable
        )).thenReturn(
                new PageImpl<>(
                        List.of(invalidCredit),
                        pageable,
                        1
                )
        );

        LedgerIntegrityResponse result =
                accountLedgerIntegrityService
                        .checkLedgerIntegrity(21L);

        assertEquals(
                LedgerIntegrityStatus.INVALID,
                result.integrityStatus()
        );

        assertTrue(result.balanceMatches());
        assertTrue(result.ledgerChainValid());
        assertFalse(
                result.operationCalculationsValid()
        );

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(issue ->
                                issue.contains(
                                        "Invalid balance calculation"
                                )
                        )
        );
    }

    @Test
    void shouldDetectStoredBalanceMismatch() {

        Account account =
                buildAccount("90.00");

        BalanceOperation credit =
                operation(
                        1L,
                        "balance-mismatch-credit",
                        BalanceOperationType.CREDIT,
                        "100.00",
                        "0.00",
                        "100.00",
                        LocalDateTime.of(
                                2026,
                                9,
                                1,
                                10,
                                0
                        )
                );

        Pageable pageable =
                auditPageable();

        when(accountRepository.findById(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByAccountId(
                21L,
                pageable
        )).thenReturn(
                new PageImpl<>(
                        List.of(credit),
                        pageable,
                        1
                )
        );

        LedgerIntegrityResponse result =
                accountLedgerIntegrityService
                        .checkLedgerIntegrity(21L);

        assertEquals(
                LedgerIntegrityStatus.INVALID,
                result.integrityStatus()
        );

        assertFalse(result.balanceMatches());
        assertTrue(result.ledgerChainValid());
        assertTrue(result.operationCalculationsValid());

        assertTrue(
                result.issues()
                        .stream()
                        .anyMatch(issue ->
                                issue.contains(
                                        "Stored account balance"
                                )
                        )
        );
    }

    @Test
    void shouldReturnNoActivityForZeroBalanceAccount() {

        Account account =
                buildAccount("0.00");

        Pageable pageable =
                auditPageable();

        when(accountRepository.findById(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByAccountId(
                21L,
                pageable
        )).thenReturn(
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                )
        );

        LedgerIntegrityResponse result =
                accountLedgerIntegrityService
                        .checkLedgerIntegrity(21L);

        assertEquals(
                LedgerIntegrityStatus.NO_ACTIVITY,
                result.integrityStatus()
        );

        assertTrue(result.balanceMatches());
        assertTrue(result.ledgerChainValid());
        assertTrue(result.operationCalculationsValid());
        assertEquals(0L, result.operationCount());
        assertTrue(result.issues().isEmpty());

        assertMoneyEquals(
                "0.00",
                result.ledgerClosingBalance()
        );
    }

    @Test
    void shouldRejectNonZeroBalanceWithoutLedger() {

        Account account =
                buildAccount("100.00");

        Pageable pageable =
                auditPageable();

        when(accountRepository.findById(21L))
                .thenReturn(Optional.of(account));

        when(balanceOperationRepository.findByAccountId(
                21L,
                pageable
        )).thenReturn(
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                )
        );

        LedgerIntegrityResponse result =
                accountLedgerIntegrityService
                        .checkLedgerIntegrity(21L);

        assertEquals(
                LedgerIntegrityStatus.INVALID,
                result.integrityStatus()
        );

        assertFalse(result.balanceMatches());

        assertTrue(
                result.issues()
                        .contains(
                                "Account has a non-zero balance "
                                        + "but no ledger operations"
                        )
        );
    }

    @Test
    void shouldRejectMissingAccount() {

        when(accountRepository.findById(999L))
                .thenReturn(Optional.empty());

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountLedgerIntegrityService
                                .checkLedgerIntegrity(999L)
                );

        assertEquals(
                ErrorCode.ACCOUNT_NOT_FOUND,
                exception.getErrorCode()
        );

        assertEquals(
                "Account not found with ID: 999",
                exception.getMessage()
        );

        verify(accountRepository).findById(999L);
        verifyNoInteractions(balanceOperationRepository);
    }

    @Test
    void shouldRejectInvalidAccountId() {

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountLedgerIntegrityService
                                .checkLedgerIntegrity(0L)
                );

        assertEquals(
                ErrorCode.INVALID_REQUEST,
                exception.getErrorCode()
        );

        assertEquals(
                "Account ID must be greater than zero",
                exception.getMessage()
        );

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(balanceOperationRepository);
    }

    private Pageable auditPageable() {

        return PageRequest.of(
                0,
                500,
                Sort.by(
                        Sort.Direction.ASC,
                        "createdAt"
                ).and(
                        Sort.by(
                                Sort.Direction.ASC,
                                "balanceOperationId"
                        )
                )
        );
    }

    private Account buildAccount(
            String balance) {

        LocalDateTime now =
                LocalDateTime.now();

        return Account.builder()
                .accountId(21L)
                .accountNumber("0000000002")
                .customerId(3L)
                .accountType(AccountType.CHECKING)
                .balance(new BigDecimal(balance))
                .accountStatus(AccountStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private BalanceOperation operation(
            Long balanceOperationId,
            String operationReference,
            BalanceOperationType operationType,
            String amount,
            String balanceBefore,
            String balanceAfter,
            LocalDateTime createdAt) {

        return BalanceOperation.builder()
                .balanceOperationId(
                        balanceOperationId
                )
                .operationReference(
                        operationReference
                )
                .transactionReference(
                        "550e8400-e29b-41d4-a716-446655440099"
                )
                .accountId(21L)
                .operationType(operationType)
                .amount(new BigDecimal(amount))
                .balanceBefore(
                        new BigDecimal(balanceBefore)
                )
                .balanceAfter(
                        new BigDecimal(balanceAfter)
                )
                .requestHash(
                        "12345678901234567890123456789012"
                                + "12345678901234567890123456789012"
                )
                .description(
                        "Day 26 ledger-integrity test"
                )
                .createdAt(createdAt)
                .build();
    }

    private void assertMoneyEquals(
            String expected,
            BigDecimal actual) {

        assertNotNull(actual);

        assertEquals(
                0,
                new BigDecimal(expected)
                        .compareTo(actual)
        );
    }
}