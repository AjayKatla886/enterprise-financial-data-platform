package com.financialplatform.account.service;

import com.financialplatform.account.dto.LedgerIntegrityResponse;
import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.BalanceOperation;
import com.financialplatform.account.entity.BalanceOperationType;
import com.financialplatform.account.entity.LedgerIntegrityStatus;
import com.financialplatform.account.exception.AccountBusinessException;
import com.financialplatform.account.repository.AccountRepository;
import com.financialplatform.account.repository.BalanceOperationRepository;
import com.financialplatform.common.exception.ErrorCode;
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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLedgerIntegrityService {

    private static final int AUDIT_BATCH_SIZE = 500;
    private static final int MAX_REPORTED_ISSUES = 100;

    private final AccountRepository accountRepository;
    private final BalanceOperationRepository balanceOperationRepository;

    @Transactional(readOnly = true)
    public LedgerIntegrityResponse checkLedgerIntegrity(
            Long accountId) {

        validateAccountId(accountId);

        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new AccountBusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account not found with ID: " + accountId
                ));

        List<String> issues =
                new ArrayList<>();

        boolean ledgerChainValid = true;
        boolean operationCalculationsValid = true;

        BalanceOperation previousOperation = null;
        BigDecimal ledgerClosingBalance = BigDecimal.ZERO;
        long operationCount = 0L;

        int pageNumber = 0;
        Page<BalanceOperation> operationPage;

        do {
            Pageable pageable = PageRequest.of(
                    pageNumber,
                    AUDIT_BATCH_SIZE,
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

            operationPage =
                    balanceOperationRepository
                            .findByAccountId(
                                    accountId,
                                    pageable
                            );

            for (BalanceOperation operation
                    : operationPage.getContent()) {

                operationCount++;

                if (!isOperationCalculationValid(operation)) {

                    operationCalculationsValid = false;

                    addIssue(
                            issues,
                            "Invalid balance calculation for operation "
                                    + operation.getOperationReference()
                    );
                }

                if (previousOperation != null
                        && previousOperation
                        .getBalanceAfter()
                        .compareTo(
                                operation.getBalanceBefore()
                        ) != 0) {

                    ledgerChainValid = false;

                    addIssue(
                            issues,
                            "Ledger chain break between operations "
                                    + previousOperation
                                    .getOperationReference()
                                    + " and "
                                    + operation
                                    .getOperationReference()
                    );
                }

                ledgerClosingBalance =
                        operation.getBalanceAfter();

                previousOperation = operation;
            }

            pageNumber++;

        } while (operationPage.hasNext());

        boolean balanceMatches;

        LedgerIntegrityStatus integrityStatus;

        if (operationCount == 0) {

            ledgerClosingBalance = BigDecimal.ZERO;

            balanceMatches =
                    account.getBalance()
                            .compareTo(BigDecimal.ZERO) == 0;

            if (balanceMatches) {
                integrityStatus =
                        LedgerIntegrityStatus.NO_ACTIVITY;
            } else {
                integrityStatus =
                        LedgerIntegrityStatus.INVALID;

                addIssue(
                        issues,
                        "Account has a non-zero balance "
                                + "but no ledger operations"
                );
            }

        } else {

            balanceMatches =
                    account.getBalance()
                            .compareTo(
                                    ledgerClosingBalance
                            ) == 0;

            if (!balanceMatches) {
                addIssue(
                        issues,
                        "Stored account balance does not match "
                                + "ledger closing balance"
                );
            }

            if (balanceMatches
                    && ledgerChainValid
                    && operationCalculationsValid) {

                integrityStatus =
                        LedgerIntegrityStatus.VALID;

            } else {

                integrityStatus =
                        LedgerIntegrityStatus.INVALID;
            }
        }

        if (issues.size() == MAX_REPORTED_ISSUES) {
            issues.add(
                    "Additional ledger issues were not included "
                            + "because the reporting limit was reached"
            );
        }

        LocalDateTime checkedAt =
                LocalDateTime.now();

        logAuditResult(
                account,
                ledgerClosingBalance,
                operationCount,
                balanceMatches,
                ledgerChainValid,
                operationCalculationsValid,
                integrityStatus
        );

        return new LedgerIntegrityResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getBalance(),
                ledgerClosingBalance,
                operationCount,
                balanceMatches,
                ledgerChainValid,
                operationCalculationsValid,
                integrityStatus,
                List.copyOf(issues),
                checkedAt
        );
    }

    private boolean isOperationCalculationValid(
            BalanceOperation operation) {

        if (operation.getBalanceBefore() == null
                || operation.getBalanceAfter() == null
                || operation.getAmount() == null
                || operation.getOperationType() == null) {

            return false;
        }

        BigDecimal expectedBalanceAfter;

        if (operation.getOperationType()
                == BalanceOperationType.CREDIT) {

            expectedBalanceAfter =
                    operation.getBalanceBefore()
                            .add(operation.getAmount());

        } else if (operation.getOperationType()
                == BalanceOperationType.DEBIT) {

            expectedBalanceAfter =
                    operation.getBalanceBefore()
                            .subtract(operation.getAmount());

        } else {

            /*
             * REVERSAL validation requires a link to the original
             * operation. Until that relationship is implemented,
             * a reversal cannot be proven mathematically here.
             */
            return false;
        }

        return expectedBalanceAfter
                .compareTo(
                        operation.getBalanceAfter()
                ) == 0;
    }

    private void addIssue(
            List<String> issues,
            String issue) {

        if (issues.size() < MAX_REPORTED_ISSUES) {
            issues.add(issue);
        }
    }

    private void validateAccountId(
            Long accountId) {

        if (accountId == null
                || accountId <= 0) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Account ID must be greater than zero"
            );
        }
    }

    private void logAuditResult(
            Account account,
            BigDecimal ledgerClosingBalance,
            long operationCount,
            boolean balanceMatches,
            boolean ledgerChainValid,
            boolean operationCalculationsValid,
            LedgerIntegrityStatus integrityStatus) {

        if (integrityStatus
                == LedgerIntegrityStatus.INVALID) {

            log.error(
                    "Account ledger integrity check failed. "
                            + "accountId={}, storedBalance={}, "
                            + "ledgerClosingBalance={}, "
                            + "operationCount={}, balanceMatches={}, "
                            + "ledgerChainValid={}, "
                            + "operationCalculationsValid={}",
                    account.getAccountId(),
                    account.getBalance(),
                    ledgerClosingBalance,
                    operationCount,
                    balanceMatches,
                    ledgerChainValid,
                    operationCalculationsValid
            );

            return;
        }

        log.info(
                "Account ledger integrity check completed. "
                        + "accountId={}, status={}, "
                        + "operationCount={}, balance={}",
                account.getAccountId(),
                integrityStatus,
                operationCount,
                account.getBalance()
        );
    }
}