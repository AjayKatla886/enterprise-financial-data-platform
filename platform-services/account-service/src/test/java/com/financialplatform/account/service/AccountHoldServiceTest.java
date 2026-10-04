package com.financialplatform.account.service;

import com.financialplatform.account.dto.AccountBalanceSummaryResponse;
import com.financialplatform.account.dto.AccountHoldRequest;
import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.AccountHold;
import com.financialplatform.account.entity.AccountHoldStatus;
import com.financialplatform.account.entity.AccountStatus;
import com.financialplatform.account.entity.AccountType;
import com.financialplatform.account.exception.AccountBusinessException;
import com.financialplatform.account.repository.AccountHoldRepository;
import com.financialplatform.account.repository.AccountRepository;
import com.financialplatform.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountHoldServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountHoldRepository accountHoldRepository;

    @InjectMocks
    private AccountHoldService accountHoldService;

    @Test
    void shouldCreateAccountHold() {

        Account account =
                buildAccount(
                        new BigDecimal("500.00"),
                        AccountStatus.ACTIVE
                );

        AccountHoldRequest request =
                holdRequest(
                        new BigDecimal("100.00")
                );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(accountHoldRepository.findByHoldReference(
                "hold-001"
        )).thenReturn(Optional.empty());

        when(accountHoldRepository.sumActiveHoldAmount(
                eq(21L),
                eq(AccountHoldStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(new BigDecimal("50.00"));

        when(accountHoldRepository.saveAndFlush(
                any(AccountHold.class)
        )).thenAnswer(invocation -> {

            AccountHold hold =
                    invocation.getArgument(0);

            hold.setAccountHoldId(1L);
            hold.setVersion(0L);

            return hold;
        });

        AccountHold result =
                accountHoldService.createHold(
                        21L,
                        "hold-001",
                        request
                );

        assertEquals(1L, result.getAccountHoldId());
        assertEquals("hold-001", result.getHoldReference());
        assertEquals(21L, result.getAccountId());

        assertEquals(
                AccountHoldStatus.ACTIVE,
                result.getHoldStatus()
        );

        assertMoneyEquals(
                "100.00",
                result.getAmount()
        );

        assertNotNull(result.getRequestHash());
        assertEquals(64, result.getRequestHash().length());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
        assertNull(result.getResolvedAt());

        /*
         * Creating a hold must not change the ledger balance.
         */
        assertMoneyEquals(
                "500.00",
                account.getBalance()
        );

        verify(accountRepository)
                .findByIdForUpdate(21L);

        verify(accountHoldRepository)
                .saveAndFlush(
                        any(AccountHold.class)
                );
    }

    @Test
    void shouldReturnExistingHoldForIdempotentRetry() {

        AccountHoldRequest request =
                holdRequest(
                        new BigDecimal("100.00")
                );

        Account account =
                buildAccount(
                        new BigDecimal("500.00"),
                        AccountStatus.ACTIVE
                );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(accountHoldRepository.findByHoldReference(
                "retry-hold-001"
        )).thenReturn(Optional.empty());

        when(accountHoldRepository.sumActiveHoldAmount(
                eq(21L),
                eq(AccountHoldStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(BigDecimal.ZERO);

        when(accountHoldRepository.saveAndFlush(
                any(AccountHold.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        AccountHold createdHold =
                accountHoldService.createHold(
                        21L,
                        "retry-hold-001",
                        request
                );

        createdHold.setAccountHoldId(1L);
        createdHold.setVersion(0L);

        reset(
                accountRepository,
                accountHoldRepository
        );

        Account retryAccount =
                buildAccount(
                        new BigDecimal("500.00"),
                        AccountStatus.ACTIVE
                );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(retryAccount));

        when(accountHoldRepository.findByHoldReference(
                "retry-hold-001"
        )).thenReturn(Optional.of(createdHold));

        AccountHold result =
                accountHoldService.createHold(
                        21L,
                        "retry-hold-001",
                        request
                );

        assertSame(createdHold, result);

        verify(accountHoldRepository, never())
                .saveAndFlush(
                        any(AccountHold.class)
                );

        verify(accountHoldRepository, never())
                .sumActiveHoldAmount(
                        anyLong(),
                        any(AccountHoldStatus.class),
                        any(LocalDateTime.class)
                );
    }

    @Test
    void shouldRejectHoldReferenceUsedWithDifferentRequest() {

        AccountHoldRequest originalRequest =
                holdRequest(
                        new BigDecimal("100.00")
                );

        Account account =
                buildAccount(
                        new BigDecimal("500.00"),
                        AccountStatus.ACTIVE
                );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(accountHoldRepository.findByHoldReference(
                "conflict-hold-001"
        )).thenReturn(Optional.empty());

        when(accountHoldRepository.sumActiveHoldAmount(
                eq(21L),
                eq(AccountHoldStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(BigDecimal.ZERO);

        when(accountHoldRepository.saveAndFlush(
                any(AccountHold.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        AccountHold existingHold =
                accountHoldService.createHold(
                        21L,
                        "conflict-hold-001",
                        originalRequest
                );

        reset(
                accountRepository,
                accountHoldRepository
        );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(accountHoldRepository.findByHoldReference(
                "conflict-hold-001"
        )).thenReturn(Optional.of(existingHold));

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountHoldService.createHold(
                                21L,
                                "conflict-hold-001",
                                holdRequest(
                                        new BigDecimal("200.00")
                                )
                        )
                );

        assertEquals(
                ErrorCode.ACCOUNT_HOLD_IDEMPOTENCY_CONFLICT,
                exception.getErrorCode()
        );

        verify(accountHoldRepository, never())
                .saveAndFlush(
                        any(AccountHold.class)
                );
    }

    @Test
    void shouldRejectHoldWhenAvailableBalanceIsInsufficient() {

        Account account =
                buildAccount(
                        new BigDecimal("500.00"),
                        AccountStatus.ACTIVE
                );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(accountHoldRepository.findByHoldReference(
                "insufficient-hold"
        )).thenReturn(Optional.empty());

        when(accountHoldRepository.sumActiveHoldAmount(
                eq(21L),
                eq(AccountHoldStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(new BigDecimal("450.00"));

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountHoldService.createHold(
                                21L,
                                "insufficient-hold",
                                holdRequest(
                                        new BigDecimal("100.00")
                                )
                        )
                );

        assertEquals(
                ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE,
                exception.getErrorCode()
        );

        assertMoneyEquals(
                "500.00",
                account.getBalance()
        );

        verify(accountHoldRepository, never())
                .saveAndFlush(
                        any(AccountHold.class)
                );
    }

    @Test
    void shouldRejectHoldForInactiveAccount() {

        Account account =
                buildAccount(
                        new BigDecimal("500.00"),
                        AccountStatus.BLOCKED
                );

        when(accountRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(account));

        when(accountHoldRepository.findByHoldReference(
                "blocked-account-hold"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountHoldService.createHold(
                                21L,
                                "blocked-account-hold",
                                holdRequest(
                                        new BigDecimal("100.00")
                                )
                        )
                );

        assertEquals(
                ErrorCode.INVALID_ACCOUNT_STATE,
                exception.getErrorCode()
        );

        verify(accountHoldRepository, never())
                .sumActiveHoldAmount(
                        anyLong(),
                        any(AccountHoldStatus.class),
                        any(LocalDateTime.class)
                );

        verify(accountHoldRepository, never())
                .saveAndFlush(
                        any(AccountHold.class)
                );
    }

    @Test
    void shouldReturnBalanceSummary() {

        Account account =
                buildAccount(
                        new BigDecimal("500.00"),
                        AccountStatus.ACTIVE
                );

        when(accountRepository.findById(21L))
                .thenReturn(Optional.of(account));

        when(accountHoldRepository.sumActiveHoldAmount(
                eq(21L),
                eq(AccountHoldStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(new BigDecimal("125.00"));

        AccountBalanceSummaryResponse result =
                accountHoldService
                        .getBalanceSummary(21L);

        assertEquals(21L, result.accountId());
        assertEquals(
                "0000000002",
                result.accountNumber()
        );
        assertEquals(
                "CHECKING",
                result.accountType()
        );

        assertMoneyEquals(
                "500.00",
                result.ledgerBalance()
        );

        assertMoneyEquals(
                "125.00",
                result.activeHoldAmount()
        );

        assertMoneyEquals(
                "375.00",
                result.availableBalance()
        );

        assertNotNull(result.calculatedAt());
    }

    @Test
    void shouldRetrieveHoldByReference() {

        AccountHold hold =
                AccountHold.builder()
                        .accountHoldId(1L)
                        .holdReference("existing-hold")
                        .transactionReference(
                                "550e8400-e29b-41d4-a716-446655440100"
                        )
                        .accountId(21L)
                        .amount(
                                new BigDecimal("100.00")
                        )
                        .holdStatus(
                                AccountHoldStatus.ACTIVE
                        )
                        .requestHash("request-hash")
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusDays(1)
                        )
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .version(0L)
                        .build();

        when(accountHoldRepository.findByHoldReference(
                "existing-hold"
        )).thenReturn(Optional.of(hold));

        AccountHold result =
                accountHoldService
                        .getByHoldReference(
                                " existing-hold "
                        );

        assertSame(hold, result);

        verify(accountHoldRepository)
                .findByHoldReference(
                        "existing-hold"
                );
    }

    @Test
    void shouldRejectMissingHold() {

        when(accountHoldRepository.findByHoldReference(
                "missing-hold"
        )).thenReturn(Optional.empty());

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountHoldService
                                .getByHoldReference(
                                        "missing-hold"
                                )
                );

        assertEquals(
                ErrorCode.ACCOUNT_HOLD_NOT_FOUND,
                exception.getErrorCode()
        );
    }

    private AccountHoldRequest holdRequest(
            BigDecimal amount) {

        return new AccountHoldRequest(
                "550e8400-e29b-41d4-a716-446655440100",
                amount,
                "Day 27 account hold test",
                LocalDateTime.now().plusDays(1)
        );
    }

    private Account buildAccount(
            BigDecimal balance,
            AccountStatus status) {

        LocalDateTime now =
                LocalDateTime.now();

        return Account.builder()
                .accountId(21L)
                .accountNumber("0000000002")
                .customerId(3L)
                .accountType(AccountType.CHECKING)
                .balance(balance)
                .accountStatus(status)
                .createdAt(now)
                .updatedAt(now)
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