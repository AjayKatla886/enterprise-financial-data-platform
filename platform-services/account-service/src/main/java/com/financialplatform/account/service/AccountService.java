package com.financialplatform.account.service;

import com.financialplatform.account.dto.AccountRequest;
import com.financialplatform.account.dto.AccountResponse;
import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.AccountStatus;
import com.financialplatform.account.mapper.AccountMapper;
import com.financialplatform.account.repository.AccountRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.financialplatform.account.exception.AccountNotFoundException;
import com.financialplatform.account.dto.AccountStatusRequest;
import com.financialplatform.account.repository.AccountSpecifications;
import com.financialplatform.common.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final EntityManager entityManager;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "accountId",
            "accountNumber",
            "customerId",
            "accountType",
            "balance",
            "accountStatus",
            "createdAt",
            "updatedAt"
    );

    @Transactional
    public AccountResponse createAccount(AccountRequest request) {

        if (accountRepository.existsByCustomerIdAndAccountType(
                request.customerId(),
                request.accountType())) {

            throw new IllegalArgumentException(
                    "Customer already has an account of type: "
                            + request.accountType()
            );
        }
        Account account = Account.builder()
                .customerId(request.customerId())
                .accountType(request.accountType())
                .balance(BigDecimal.ZERO)
                .accountStatus(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Account savedAccount = accountRepository.saveAndFlush(account);

        entityManager.refresh(savedAccount);

        return AccountMapper.toResponse(savedAccount);
    }
    public AccountResponse getAccountById(Long accountId) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(accountId));

        return AccountMapper.toResponse(account);
    }
    public List<AccountResponse> getAccountsByCustomerId(Long customerId) {

        return accountRepository.findByCustomerId(customerId)
                .stream()
                .map(AccountMapper::toResponse)
                .toList();
    }
    @Transactional
    public AccountResponse updateAccountStatus(
            Long accountId,
            AccountStatusRequest request) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(accountId));
        if (account.getAccountStatus() == AccountStatus.CLOSED) {
            throw new IllegalArgumentException(
                    "Closed account status cannot be changed"
            );
        }
        account.setAccountStatus(request.accountStatus());
        account.setUpdatedAt(LocalDateTime.now());

        Account updatedAccount = accountRepository.save(account);

        return AccountMapper.toResponse(updatedAccount);
    }
    @Transactional
    public AccountResponse closeAccount(Long accountId) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(accountId));
        if (account.getAccountStatus() == AccountStatus.CLOSED) {
            throw new IllegalArgumentException(
                    "Account is already closed"
            );
        }

        account.setAccountStatus(AccountStatus.CLOSED);
        account.setUpdatedAt(LocalDateTime.now());

        Account updatedAccount = accountRepository.save(account);

        return AccountMapper.toResponse(updatedAccount);
    }

    public PageResponse<AccountResponse> getAccounts(
            Long customerId,
            String accountType,
            String status,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException(
                    "Invalid sort field: " + sortBy
            );
        }

        if (!sortDir.equalsIgnoreCase("asc")
                && !sortDir.equalsIgnoreCase("desc")) {

            throw new IllegalArgumentException(
                    "Sort direction must be either 'asc' or 'desc'"
            );
        }

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(
                page,
                size,
                sort
        );

        Page<Account> accountPage =
                accountRepository.findAll(
                        AccountSpecifications.hasCustomerId(customerId)
                                .and(AccountSpecifications.hasAccountType(accountType))
                                .and(AccountSpecifications.hasStatus(status)),
                        pageable
                );

        return new PageResponse<>(
                accountPage.getContent()
                        .stream()
                        .map(AccountMapper::toResponse)
                        .toList(),
                accountPage.getNumber(),
                accountPage.getSize(),
                accountPage.getTotalElements(),
                accountPage.getTotalPages(),
                accountPage.isFirst(),
                accountPage.isLast()
        );
    }
}