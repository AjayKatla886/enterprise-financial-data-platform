package com.financialplatform.account.service;

import com.financialplatform.account.dto.BalanceOperationRequest;
import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.AccountStatus;
import com.financialplatform.account.entity.BalanceOperation;
import com.financialplatform.account.entity.BalanceOperationType;
import com.financialplatform.account.exception.AccountBusinessException;
import com.financialplatform.account.repository.AccountRepository;
import com.financialplatform.account.repository.BalanceOperationRepository;
import com.financialplatform.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceOperationService {

    private final AccountRepository accountRepository;
    private final BalanceOperationRepository balanceOperationRepository;

    @Transactional
    public BalanceOperation applyBalanceOperation(
            Long accountId,
            String operationReference,
            BalanceOperationRequest request) {

        validateOperationReference(operationReference);

        /*
         * Lock the account before checking idempotency.
         * Requests for the same account are processed one at a time.
         */
        Account account = accountRepository
                .findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountBusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND,
                        "Account not found with ID: " + accountId
                ));

        String requestHash = generateRequestHash(
                accountId,
                request
        );

        /*
         * If the operation was already completed, return the original
         * result instead of updating the balance again.
         */
        BalanceOperation existingOperation =
                balanceOperationRepository
                        .findByOperationReference(operationReference)
                        .orElse(null);

        if (existingOperation != null) {

            if (!existingOperation.getRequestHash().equals(requestHash)) {
                throw new AccountBusinessException(
                        ErrorCode.BALANCE_OPERATION_IDEMPOTENCY_CONFLICT,
                        "Operation reference was already used with different request data"
                );
            }

            log.info(
                    "Returning existing balance operation. operationReference={}, accountId={}",
                    operationReference,
                    accountId
            );

            return existingOperation;
        }

        validateAccountStatus(account);
        validateSupportedOperation(request.operationType());

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = calculateBalanceAfter(
                accountId,
                balanceBefore,
                request.operationType(),
                request.amount()
        );

        LocalDateTime now = LocalDateTime.now();

        account.setBalance(balanceAfter);
        account.setUpdatedAt(now);

        /*
         * The locked Account entity is managed by JPA.
         * The balance update and ledger insertion commit together.
         */
        BalanceOperation operation = BalanceOperation.builder()
                .operationReference(operationReference)
                .transactionReference(
                        request.transactionReference().trim()
                )
                .accountId(accountId)
                .operationType(request.operationType())
                .amount(request.amount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .requestHash(requestHash)
                .description(normalizeDescription(request.description()))
                .createdAt(now)
                .build();

        BalanceOperation savedOperation =
                balanceOperationRepository.save(operation);

        log.info(
                "Account balance operation completed. accountId={}, operationReference={}, operationType={}, amount={}, balanceBefore={}, balanceAfter={}",
                accountId,
                operationReference,
                request.operationType(),
                request.amount(),
                balanceBefore,
                balanceAfter
        );

        return savedOperation;
    }

    @Transactional(readOnly = true)
    public BalanceOperation getByOperationReference(
            String operationReference) {

        return balanceOperationRepository
                .findByOperationReference(operationReference)
                .orElseThrow(() -> new AccountBusinessException(
                        ErrorCode.BALANCE_OPERATION_NOT_FOUND,
                        "Balance operation not found with reference: "
                                + operationReference
                ));
    }

    private BigDecimal calculateBalanceAfter(
            Long accountId,
            BigDecimal balanceBefore,
            BalanceOperationType operationType,
            BigDecimal amount) {

        if (operationType == BalanceOperationType.DEBIT) {

            if (balanceBefore.compareTo(amount) < 0) {
                throw new AccountBusinessException(
                        ErrorCode.INSUFFICIENT_FUNDS,
                        "Insufficient funds for account ID: " + accountId
                );
            }

            return balanceBefore.subtract(amount);
        }

        if (operationType == BalanceOperationType.CREDIT) {
            return balanceBefore.add(amount);
        }

        throw new AccountBusinessException(
                ErrorCode.INVALID_REQUEST,
                "Unsupported balance operation type: " + operationType
        );
    }

    private void validateAccountStatus(Account account) {

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountBusinessException(
                    ErrorCode.INVALID_ACCOUNT_STATE,
                    "Balance operation requires an active account. accountId="
                            + account.getAccountId()
            );
        }
    }

    private void validateSupportedOperation(
            BalanceOperationType operationType) {

        if (operationType == BalanceOperationType.REVERSAL) {
            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Direct REVERSAL operations are not supported"
            );
        }
    }

    private void validateOperationReference(
            String operationReference) {

        if (operationReference == null
                || operationReference.isBlank()) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Operation-Reference header is required"
            );
        }

        if (operationReference.length() > 150) {
            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Operation-Reference must not exceed 150 characters"
            );
        }
    }

    private String generateRequestHash(
            Long accountId,
            BalanceOperationRequest request) {

        String canonicalRequest = String.join(
                "|",
                accountId.toString(),
                request.transactionReference().trim(),
                request.operationType().name(),
                normalizeAmount(request.amount()),
                normalizeDescription(request.description())
        );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    ex
            );
        }
    }

    private String normalizeAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private String normalizeDescription(String description) {

        if (description == null) {
            return "";
        }

        return description.trim();
    }
}