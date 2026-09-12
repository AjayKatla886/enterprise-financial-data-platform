package com.financialplatform.account.service;

import com.financialplatform.account.client.CustomerClient;
import com.financialplatform.account.dto.AccountRequest;
import com.financialplatform.account.dto.AccountResponse;
import com.financialplatform.account.dto.AccountStatusRequest;
import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.AccountStatus;
import com.financialplatform.account.entity.AccountType;
import com.financialplatform.account.exception.AccountNotFoundException;
import com.financialplatform.account.exception.CustomerServiceUnavailableException;
import com.financialplatform.account.repository.AccountRepository;
import com.financialplatform.account.exception.AccountBusinessException;
import com.financialplatform.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private CustomerClient customerClient;

    @InjectMocks
    private AccountService accountService;

    private AccountRequest request;

    @BeforeEach
    void setUp() {
        request = new AccountRequest(
                3L,
                AccountType.SAVINGS
        );
    }

    @Test
    void shouldCreateAccountForActiveCustomer() {

        CustomerClient.CustomerLookupResponse customer =
                new CustomerClient.CustomerLookupResponse(
                        3L,
                        "000003",
                        "Priya",
                        "Reddy",
                        "ACTIVE"
                );

        when(customerClient.getCustomerById(3L))
                .thenReturn(customer);

        when(customerClient.isCustomerKycVerified(3L))
                .thenReturn(true);

        when(accountRepository.existsByCustomerIdAndAccountType(
                3L,
                AccountType.SAVINGS))
                .thenReturn(false);

        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> {
                    Account account = invocation.getArgument(0);

                    account.setAccountId(10L);
                    account.setAccountNumber("0000000010");

                    return account;
                });

        AccountResponse response =
                accountService.createAccount(request);

        assertNotNull(response);

        assertEquals(10L, response.accountId());
        assertEquals("0000000010", response.accountNumber());
        assertEquals(3L, response.customerId());
        assertEquals("SAVINGS", response.accountType());
        assertEquals(BigDecimal.ZERO, response.balance());
        assertEquals("ACTIVE", response.accountStatus());

        verify(customerClient).getCustomerById(3L);

        verify(customerClient)
                .isCustomerKycVerified(3L);

        verify(accountRepository)
                .existsByCustomerIdAndAccountType(
                        3L,
                        AccountType.SAVINGS
                );

        verify(accountRepository)
                .saveAndFlush(any(Account.class));

        verify(entityManager)
                .refresh(any(Account.class));
    }
    @Test
    void shouldRejectAccountCreationForInactiveCustomer() {

        CustomerClient.CustomerLookupResponse customer =
                new CustomerClient.CustomerLookupResponse(
                        2L,
                        "000002",
                        "Rahul",
                        "Sharma",
                        "INACTIVE"
                );

        when(customerClient.getCustomerById(2L))
                .thenReturn(customer);

        AccountRequest inactiveCustomerRequest =
                new AccountRequest(
                        2L,
                        AccountType.SAVINGS
                );

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountService.createAccount(inactiveCustomerRequest)
                );

        assertEquals(ErrorCode.CUSTOMER_INACTIVE, exception.getErrorCode());

        assertEquals(
                "Account cannot be created for an inactive customer",
                exception.getMessage()
        );

        verify(customerClient)
                .getCustomerById(2L);

        verify(accountRepository, never())
                .saveAndFlush(any(Account.class));
    }
    @Test
    void shouldRejectAccountCreationWhenCustomerDoesNotExist() {

        when(customerClient.getCustomerById(999999L))
                .thenThrow(
                        new IllegalArgumentException(
                                "Customer not found with ID: 999999"
                        )
                );

        AccountRequest request =
                new AccountRequest(
                        999999L,
                        AccountType.CHECKING
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> accountService.createAccount(request)
                );

        assertEquals(
                "Customer not found with ID: 999999",
                exception.getMessage()
        );

        verify(customerClient)
                .getCustomerById(999999L);

        verify(accountRepository, never())
                .saveAndFlush(any(Account.class));
    }
    @Test
    void shouldRejectAccountCreationWhenCustomerServiceIsUnavailable() {

        when(customerClient.getCustomerById(3L))
                .thenThrow(
                        new CustomerServiceUnavailableException(
                                "Customer Service is currently unavailable"
                        )
                );

        AccountRequest request =
                new AccountRequest(
                        3L,
                        AccountType.CHECKING
                );

        CustomerServiceUnavailableException exception =
                assertThrows(
                        CustomerServiceUnavailableException.class,
                        () -> accountService.createAccount(request)
                );

        assertEquals(
                "Customer Service is currently unavailable",
                exception.getMessage()
        );

        verify(customerClient)
                .getCustomerById(3L);

        verify(accountRepository, never())
                .saveAndFlush(any(Account.class));
    }
    @Test
    void shouldRejectDuplicateAccountTypeForCustomer() {

        CustomerClient.CustomerLookupResponse customer =
                new CustomerClient.CustomerLookupResponse(
                        3L,
                        "000003",
                        "Priya",
                        "Reddy",
                        "ACTIVE"
                );

        when(customerClient.getCustomerById(3L))
                .thenReturn(customer);

        when(customerClient.isCustomerKycVerified(3L))
                .thenReturn(true);

        when(accountRepository.existsByCustomerIdAndAccountType(
                3L,
                AccountType.SAVINGS))
                .thenReturn(true);

        AccountRequest request =
                new AccountRequest(
                        3L,
                        AccountType.SAVINGS
                );

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountService.createAccount(request)
                );

        assertEquals(ErrorCode.DUPLICATE_ACCOUNT_TYPE, exception.getErrorCode());

        assertEquals(
                "Customer already has an account of type: SAVINGS",
                exception.getMessage()
        );

        verify(customerClient)
                .getCustomerById(3L);

        verify(customerClient)
                .isCustomerKycVerified(3L);

        verify(accountRepository)
                .existsByCustomerIdAndAccountType(
                        3L,
                        AccountType.SAVINGS
                );
    }
    @Test
    void shouldRejectStatusChangeForClosedAccount() {

        Account closedAccount = Account.builder()
                .accountId(10L)
                .accountNumber("0000000010")
                .customerId(3L)
                .accountType(AccountType.SAVINGS)
                .balance(BigDecimal.ZERO)
                .accountStatus(AccountStatus.CLOSED)
                .build();

        when(accountRepository.findById(10L))
                .thenReturn(java.util.Optional.of(closedAccount));

        AccountStatusRequest request =
                new AccountStatusRequest(AccountStatus.ACTIVE);

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountService.updateAccountStatus(10L, request)
                );

        assertEquals(ErrorCode.INVALID_ACCOUNT_STATE, exception.getErrorCode());

        assertEquals(
                "Closed account status cannot be changed",
                exception.getMessage()
        );

        verify(accountRepository).findById(10L);

        verify(accountRepository, never())
                .save(any(Account.class));
    }
    @Test
    void shouldCloseActiveAccountSuccessfully() {

        Account activeAccount = Account.builder()
                .accountId(10L)
                .accountNumber("0000000010")
                .customerId(3L)
                .accountType(AccountType.SAVINGS)
                .balance(BigDecimal.ZERO)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findById(10L))
                .thenReturn(java.util.Optional.of(activeAccount));

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response =
                accountService.closeAccount(10L);

        assertNotNull(response);
        assertEquals("CLOSED", response.accountStatus());
        assertNotNull(response.updatedAt());

        verify(accountRepository).findById(10L);
        verify(accountRepository).save(activeAccount);

        assertEquals(
                AccountStatus.CLOSED,
                activeAccount.getAccountStatus()
        );
    }
    @Test
    void shouldRejectClosingAlreadyClosedAccount() {

        Account closedAccount = Account.builder()
                .accountId(10L)
                .accountNumber("0000000010")
                .customerId(3L)
                .accountType(AccountType.SAVINGS)
                .balance(BigDecimal.ZERO)
                .accountStatus(AccountStatus.CLOSED)
                .build();

        when(accountRepository.findById(10L))
                .thenReturn(java.util.Optional.of(closedAccount));

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountService.closeAccount(10L)
                );

        assertEquals(ErrorCode.ACCOUNT_ALREADY_CLOSED, exception.getErrorCode());

        assertEquals(
                "Account is already closed",
                exception.getMessage()
        );

        verify(accountRepository).findById(10L);

        verify(accountRepository, never())
                .save(any(Account.class));
    }
    @Test
    void shouldGetAccountByIdSuccessfully() {

        Account account = Account.builder()
                .accountId(10L)
                .accountNumber("0000000010")
                .customerId(3L)
                .accountType(AccountType.SAVINGS)
                .balance(BigDecimal.ZERO)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findById(10L))
                .thenReturn(java.util.Optional.of(account));

        AccountResponse response =
                accountService.getAccountById(10L);

        assertNotNull(response);
        assertEquals(10L, response.accountId());
        assertEquals("0000000010", response.accountNumber());
        assertEquals(3L, response.customerId());
        assertEquals("SAVINGS", response.accountType());
        assertEquals(BigDecimal.ZERO, response.balance());
        assertEquals("ACTIVE", response.accountStatus());

        verify(accountRepository).findById(10L);
    }
    @Test
    void shouldThrowExceptionWhenAccountNotFound() {

        when(accountRepository.findById(999L))
                .thenReturn(java.util.Optional.empty());

        AccountNotFoundException exception =
                assertThrows(
                        AccountNotFoundException.class,
                        () -> accountService.getAccountById(999L)
                );

        assertEquals(
                "Account not found with ID: 999",
                exception.getMessage()
        );

        verify(accountRepository).findById(999L);
    }
    @Test
    void shouldRejectAccountCreationWhenKycNotVerified() {

        CustomerClient.CustomerLookupResponse customer =
                new CustomerClient.CustomerLookupResponse(
                        3L,
                        "000003",
                        "Priya",
                        "Reddy",
                        "ACTIVE"
                );

        when(customerClient.getCustomerById(3L))
                .thenReturn(customer);

        when(customerClient.isCustomerKycVerified(3L))
                .thenReturn(false);

        AccountBusinessException exception =
                assertThrows(
                        AccountBusinessException.class,
                        () -> accountService.createAccount(request)
                );

        assertEquals(ErrorCode.CUSTOMER_KYC_NOT_VERIFIED, exception.getErrorCode());

        assertEquals(
                "Account cannot be created because customer KYC is not verified. customerId=3",
                exception.getMessage()
        );

        verify(customerClient)
                .getCustomerById(3L);

        verify(customerClient)
                .isCustomerKycVerified(3L);

        verify(accountRepository, never())
                .existsByCustomerIdAndAccountType(
                        anyLong(),
                        any(AccountType.class)
                );

        verify(accountRepository, never())
                .saveAndFlush(any(Account.class));
    }
    @Test
    void shouldRejectClosureWithPositiveBalance() {
        assertClosureRejected(new BigDecimal("100.00"), false);
    }

    @Test
    void shouldRejectClosureWithNegativeBalance() {
        assertClosureRejected(new BigDecimal("-25.00"), false);
    }

    @Test
    void shouldRejectStatusClosureWithPositiveBalance() {
        assertClosureRejected(new BigDecimal("100.00"), true);
    }

    @Test
    void shouldRejectStatusClosureWithNegativeBalance() {
        assertClosureRejected(new BigDecimal("-25.00"), true);
    }

    @Test
    void shouldCloseAccountWithScaledZeroBalance() {
        Account account = buildLifecycleAccount(new BigDecimal("0.00"));

        when(accountRepository.findById(10L))
                .thenReturn(java.util.Optional.of(account));

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.closeAccount(10L);

        assertEquals("CLOSED", response.accountStatus());
        assertEquals(AccountStatus.CLOSED, account.getAccountStatus());
        assertNotNull(response.updatedAt());

        verify(accountRepository).save(account);
    }

    @Test
    void shouldCloseThroughStatusUpdateWithZeroBalance() {
        Account account = buildLifecycleAccount(new BigDecimal("0.00"));

        when(accountRepository.findById(10L))
                .thenReturn(java.util.Optional.of(account));

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.updateAccountStatus(
                10L,
                new AccountStatusRequest(AccountStatus.CLOSED)
        );

        assertEquals("CLOSED", response.accountStatus());
        assertEquals(AccountStatus.CLOSED, account.getAccountStatus());
        assertNotNull(response.updatedAt());

        verify(accountRepository).save(account);
    }

    @Test
    void shouldAllowBlockingAccountWithNonzeroBalance() {
        Account account = buildLifecycleAccount(new BigDecimal("100.00"));

        when(accountRepository.findById(10L))
                .thenReturn(java.util.Optional.of(account));

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.updateAccountStatus(
                10L,
                new AccountStatusRequest(AccountStatus.BLOCKED)
        );

        assertEquals("BLOCKED", response.accountStatus());
        assertEquals(new BigDecimal("100.00"), account.getBalance());

        verify(accountRepository).save(account);
    }

    private void assertClosureRejected(
            BigDecimal balance,
            boolean throughStatusUpdate) {

        Account account = buildLifecycleAccount(balance);

        java.time.LocalDateTime originalUpdatedAt =
                account.getUpdatedAt();

        when(accountRepository.findById(10L))
                .thenReturn(java.util.Optional.of(account));

        AccountBusinessException exception = assertThrows(
                AccountBusinessException.class,
                () -> {
                    if (throughStatusUpdate) {
                        accountService.updateAccountStatus(
                                10L,
                                new AccountStatusRequest(AccountStatus.CLOSED)
                        );
                    } else {
                        accountService.closeAccount(10L);
                    }
                }
        );

        assertEquals(
                ErrorCode.INVALID_ACCOUNT_STATE,
                exception.getErrorCode()
        );

        assertEquals(
                "Account can be closed only when balance is zero",
                exception.getMessage()
        );

        assertEquals(AccountStatus.ACTIVE, account.getAccountStatus());
        assertEquals(balance, account.getBalance());
        assertEquals(originalUpdatedAt, account.getUpdatedAt());

        verify(accountRepository, never()).save(any(Account.class));
    }

    private Account buildLifecycleAccount(BigDecimal balance) {
        java.time.LocalDateTime createdAt =
                java.time.LocalDateTime.of(2026, 9, 1, 10, 0);

        return Account.builder()
                .accountId(10L)
                .accountNumber("0000000010")
                .customerId(3L)
                .accountType(AccountType.SAVINGS)
                .balance(balance)
                .accountStatus(AccountStatus.ACTIVE)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}