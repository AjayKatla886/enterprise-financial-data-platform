package com.financialplatform.account.service;

import com.financialplatform.account.dto.AccountRequest;
import com.financialplatform.account.dto.AccountResponse;
import com.financialplatform.account.dto.AccountStatusRequest;
import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.AccountStatus;
import com.financialplatform.account.exception.AccountNotFoundException;
import com.financialplatform.account.mapper.AccountMapper;
import com.financialplatform.account.repository.AccountRepository;
import com.financialplatform.account.repository.AccountSpecifications;
import com.financialplatform.common.response.PageResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
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

        log.info(
                "Account creation requested. customerId={}, accountType={}",
                request.customerId(),
                request.accountType()
        );

        if (accountRepository.existsByCustomerIdAndAccountType(
                request.customerId(),
                request.accountType())) {

            log.warn(
                    "Account creation rejected because customer already has requested account type. customerId={}, accountType={}",
                    request.customerId(),
                    request.accountType()
            );

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

        Account savedAccount =
                accountRepository.saveAndFlush(account);

        entityManager.refresh(savedAccount);

        log.info(
                "Account created successfully. accountId={}, customerId={}, accountType={}",
                savedAccount.getAccountId(),
                savedAccount.getCustomerId(),
                savedAccount.getAccountType()
        );

        return AccountMapper.toResponse(savedAccount);
    }

    public AccountResponse getAccountById(Long accountId) {

        log.debug(
                "Fetching account. accountId={}",
                accountId
        );

        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(() ->
                                new AccountNotFoundException(accountId)
                        );

        return AccountMapper.toResponse(account);
    }

    public List<AccountResponse> getAccountsByCustomerId(
            Long customerId
    ) {

        log.debug(
                "Fetching accounts for customer. customerId={}",
                customerId
        );

        List<AccountResponse> accounts =
                accountRepository.findByCustomerId(customerId)
                        .stream()
                        .map(AccountMapper::toResponse)
                        .toList();

        log.debug(
                "Account lookup by customer completed. customerId={}, returned={}",
                customerId,
                accounts.size()
        );

        return accounts;
    }

    @Transactional
    public AccountResponse updateAccountStatus(
            Long accountId,
            AccountStatusRequest request
    ) {

        log.info(
                "Account status update requested. accountId={}, requestedStatus={}",
                accountId,
                request.accountStatus()
        );

        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(() ->
                                new AccountNotFoundException(accountId)
                        );

        if (account.getAccountStatus()
                == AccountStatus.CLOSED) {

            log.warn(
                    "Account status update rejected because account is closed. accountId={}",
                    accountId
            );

            throw new IllegalArgumentException(
                    "Closed account status cannot be changed"
            );
        }

        AccountStatus previousStatus =
                account.getAccountStatus();

        account.setAccountStatus(
                request.accountStatus()
        );

        account.setUpdatedAt(
                LocalDateTime.now()
        );

        Account updatedAccount =
                accountRepository.save(account);

        log.info(
                "Account status updated successfully. accountId={}, previousStatus={}, newStatus={}",
                accountId,
                previousStatus,
                updatedAccount.getAccountStatus()
        );

        return AccountMapper.toResponse(updatedAccount);
    }

    @Transactional
    public AccountResponse closeAccount(
            Long accountId
    ) {

        log.info(
                "Account close requested. accountId={}",
                accountId
        );

        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(() ->
                                new AccountNotFoundException(accountId)
                        );

        if (account.getAccountStatus()
                == AccountStatus.CLOSED) {

            log.warn(
                    "Account close rejected because account is already closed. accountId={}",
                    accountId
            );

            throw new IllegalArgumentException(
                    "Account is already closed"
            );
        }

        account.setAccountStatus(
                AccountStatus.CLOSED
        );

        account.setUpdatedAt(
                LocalDateTime.now()
        );

        Account updatedAccount =
                accountRepository.save(account);

        log.info(
                "Account closed successfully. accountId={}",
                accountId
        );

        return AccountMapper.toResponse(updatedAccount);
    }

    public PageResponse<AccountResponse> getAccounts(
            Long customerId,
            String accountType,
            String status,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {

        log.debug(
                "Fetching accounts. customerId={}, accountType={}, status={}, page={}, size={}, sortBy={}, sortDir={}",
                customerId,
                accountType,
                status,
                page,
                size,
                sortBy,
                sortDir
        );

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {

            log.warn(
                    "Invalid account sort field requested. sortBy={}",
                    sortBy
            );

            throw new IllegalArgumentException(
                    "Invalid sort field: " + sortBy
            );
        }

        if (!sortDir.equalsIgnoreCase("asc")
                && !sortDir.equalsIgnoreCase("desc")) {

            log.warn(
                    "Invalid account sort direction requested. sortDir={}",
                    sortDir
            );

            throw new IllegalArgumentException(
                    "Sort direction must be either 'asc' or 'desc'"
            );
        }

        Sort sort =
                sortDir.equalsIgnoreCase("asc")
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        sort
                );

        Page<Account> accountPage =
                accountRepository.findAll(
                        AccountSpecifications
                                .hasCustomerId(customerId)
                                .and(
                                        AccountSpecifications
                                                .hasAccountType(accountType)
                                )
                                .and(
                                        AccountSpecifications
                                                .hasStatus(status)
                                ),
                        pageable
                );

        List<AccountResponse> accounts =
                accountPage.getContent()
                        .stream()
                        .map(AccountMapper::toResponse)
                        .toList();

        log.debug(
                "Account query completed. returned={}, totalElements={}, totalPages={}",
                accounts.size(),
                accountPage.getTotalElements(),
                accountPage.getTotalPages()
        );

        return new PageResponse<>(
                accounts,
                accountPage.getNumber(),
                accountPage.getSize(),
                accountPage.getTotalElements(),
                accountPage.getTotalPages(),
                accountPage.isFirst(),
                accountPage.isLast()
        );
    }
}