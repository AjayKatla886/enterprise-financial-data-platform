package com.financialplatform.account.service;

import com.financialplatform.account.dto.AccountTransferRequest;
import com.financialplatform.account.dto.AccountTransferResponse;
import com.financialplatform.account.dto.BalanceOperationRequest;
import com.financialplatform.account.dto.BalanceOperationResponse;
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

    private static final int MAX_OPERATION_REFERENCE_LENGTH = 150;
    private static final int MAX_TRANSFER_REFERENCE_LENGTH = 143;

    private final AccountRepository accountRepository;
    private final BalanceOperationRepository balanceOperationRepository;

    @Transactional
    public BalanceOperation applyBalanceOperation(
            Long accountId,
            String operationReference,
            BalanceOperationRequest request) {

        String normalizedOperationReference =
                validateAndNormalizeOperationReference(
                        operationReference,
                        MAX_OPERATION_REFERENCE_LENGTH
                );

        Account account = accountRepository
                .findByIdForUpdate(accountId)
                .orElseThrow(() -> accountNotFound(accountId));

        String requestHash =
                generateRequestHash(
                        accountId,
                        request
                );

        BalanceOperation existingOperation =
                balanceOperationRepository
                        .findByOperationReference(
                                normalizedOperationReference
                        )
                        .orElse(null);

        if (existingOperation != null) {

            validateExistingOperation(
                    existingOperation,
                    requestHash
            );

            log.info(
                    "Returning existing balance operation. "
                            + "operationReference={}, accountId={}",
                    normalizedOperationReference,
                    accountId
            );

            return existingOperation;
        }

        validateAccountStatus(account);
        validateSupportedOperation(request.operationType());

        BigDecimal balanceBefore =
                account.getBalance();

        BigDecimal balanceAfter =
                calculateBalanceAfter(
                        accountId,
                        balanceBefore,
                        request.operationType(),
                        request.amount()
                );

        LocalDateTime now = LocalDateTime.now();

        account.setBalance(balanceAfter);
        account.setUpdatedAt(now);

        BalanceOperation operation =
                buildBalanceOperation(
                        normalizedOperationReference,
                        request.transactionReference().trim(),
                        accountId,
                        request.operationType(),
                        request.amount(),
                        balanceBefore,
                        balanceAfter,
                        requestHash,
                        request.description(),
                        now
                );

        BalanceOperation savedOperation =
                balanceOperationRepository.save(operation);

        log.info(
                "Account balance operation completed. "
                        + "accountId={}, operationReference={}, "
                        + "operationType={}, amount={}, "
                        + "balanceBefore={}, balanceAfter={}",
                accountId,
                normalizedOperationReference,
                request.operationType(),
                request.amount(),
                balanceBefore,
                balanceAfter
        );

        return savedOperation;
    }

    @Transactional
    public AccountTransferResponse applyTransfer(
            String operationReference,
            AccountTransferRequest request) {

        String normalizedOperationReference =
                validateAndNormalizeOperationReference(
                        operationReference,
                        MAX_TRANSFER_REFERENCE_LENGTH
                );

        if (request.sourceAccountId()
                .equals(request.targetAccountId())) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Source and target accounts must be different"
            );
        }

        String debitOperationReference =
                normalizedOperationReference + "-debit";

        String creditOperationReference =
                normalizedOperationReference + "-credit";

        /*
         * Both transfers 21 -> 22 and 22 -> 21 lock the rows
         * in the same order. This prevents database deadlocks.
         */
        Long firstAccountId = Math.min(
                request.sourceAccountId(),
                request.targetAccountId()
        );

        Long secondAccountId = Math.max(
                request.sourceAccountId(),
                request.targetAccountId()
        );

        Account firstLockedAccount =
                accountRepository
                        .findByIdForUpdate(firstAccountId)
                        .orElseThrow(() ->
                                accountNotFound(firstAccountId)
                        );

        Account secondLockedAccount =
                accountRepository
                        .findByIdForUpdate(secondAccountId)
                        .orElseThrow(() ->
                                accountNotFound(secondAccountId)
                        );

        Account sourceAccount =
                firstLockedAccount.getAccountId()
                        .equals(request.sourceAccountId())
                        ? firstLockedAccount
                        : secondLockedAccount;

        Account targetAccount =
                firstLockedAccount.getAccountId()
                        .equals(request.targetAccountId())
                        ? firstLockedAccount
                        : secondLockedAccount;

        BalanceOperationRequest debitRequest =
                new BalanceOperationRequest(
                        request.transactionReference(),
                        BalanceOperationType.DEBIT,
                        request.amount(),
                        request.description()
                );

        BalanceOperationRequest creditRequest =
                new BalanceOperationRequest(
                        request.transactionReference(),
                        BalanceOperationType.CREDIT,
                        request.amount(),
                        request.description()
                );

        String debitRequestHash =
                generateRequestHash(
                        request.sourceAccountId(),
                        debitRequest
                );

        String creditRequestHash =
                generateRequestHash(
                        request.targetAccountId(),
                        creditRequest
                );

        BalanceOperation existingDebit =
                balanceOperationRepository
                        .findByOperationReference(
                                debitOperationReference
                        )
                        .orElse(null);

        BalanceOperation existingCredit =
                balanceOperationRepository
                        .findByOperationReference(
                                creditOperationReference
                        )
                        .orElse(null);

        /*
         * A successful retry must find both ledger records.
         */
        if (existingDebit != null
                && existingCredit != null) {

            validateExistingOperation(
                    existingDebit,
                    debitRequestHash
            );

            validateExistingOperation(
                    existingCredit,
                    creditRequestHash
            );

            log.info(
                    "Returning existing atomic transfer. "
                            + "transactionReference={}, "
                            + "sourceAccountId={}, targetAccountId={}",
                    request.transactionReference(),
                    request.sourceAccountId(),
                    request.targetAccountId()
            );

            return buildTransferResponse(
                    request.transactionReference(),
                    existingDebit,
                    existingCredit
            );
        }

        /*
         * Because both records are inserted in one database transaction,
         * finding only one indicates an inconsistent or conflicting key.
         */
        if (existingDebit != null
                || existingCredit != null) {

            throw new AccountBusinessException(
                    ErrorCode.BALANCE_OPERATION_IDEMPOTENCY_CONFLICT,
                    "Transfer operation reference has an incomplete "
                            + "or conflicting ledger state"
            );
        }

        validateAccountStatus(sourceAccount);
        validateAccountStatus(targetAccount);

        BigDecimal sourceBalanceBefore =
                sourceAccount.getBalance();

        BigDecimal sourceBalanceAfter =
                calculateBalanceAfter(
                        sourceAccount.getAccountId(),
                        sourceBalanceBefore,
                        BalanceOperationType.DEBIT,
                        request.amount()
                );

        BigDecimal targetBalanceBefore =
                targetAccount.getBalance();

        BigDecimal targetBalanceAfter =
                calculateBalanceAfter(
                        targetAccount.getAccountId(),
                        targetBalanceBefore,
                        BalanceOperationType.CREDIT,
                        request.amount()
                );

        LocalDateTime now = LocalDateTime.now();

        sourceAccount.setBalance(sourceBalanceAfter);
        sourceAccount.setUpdatedAt(now);

        targetAccount.setBalance(targetBalanceAfter);
        targetAccount.setUpdatedAt(now);

        BalanceOperation debitOperation =
                buildBalanceOperation(
                        debitOperationReference,
                        request.transactionReference().trim(),
                        sourceAccount.getAccountId(),
                        BalanceOperationType.DEBIT,
                        request.amount(),
                        sourceBalanceBefore,
                        sourceBalanceAfter,
                        debitRequestHash,
                        request.description(),
                        now
                );

        BalanceOperation creditOperation =
                buildBalanceOperation(
                        creditOperationReference,
                        request.transactionReference().trim(),
                        targetAccount.getAccountId(),
                        BalanceOperationType.CREDIT,
                        request.amount(),
                        targetBalanceBefore,
                        targetBalanceAfter,
                        creditRequestHash,
                        request.description(),
                        now
                );

        BalanceOperation savedDebit =
                balanceOperationRepository
                        .save(debitOperation);

        BalanceOperation savedCredit =
                balanceOperationRepository
                        .save(creditOperation);

        log.info(
                "Atomic account transfer completed. "
                        + "transactionReference={}, "
                        + "sourceAccountId={}, targetAccountId={}, "
                        + "amount={}, sourceBalanceAfter={}, "
                        + "targetBalanceAfter={}",
                request.transactionReference(),
                sourceAccount.getAccountId(),
                targetAccount.getAccountId(),
                request.amount(),
                sourceBalanceAfter,
                targetBalanceAfter
        );

        return buildTransferResponse(
                request.transactionReference(),
                savedDebit,
                savedCredit
        );
    }

    @Transactional(readOnly = true)
    public BalanceOperation getByOperationReference(
            String operationReference) {

        String normalizedOperationReference =
                validateAndNormalizeOperationReference(
                        operationReference,
                        MAX_OPERATION_REFERENCE_LENGTH
                );

        return balanceOperationRepository
                .findByOperationReference(
                        normalizedOperationReference
                )
                .orElseThrow(() ->
                        new AccountBusinessException(
                                ErrorCode.BALANCE_OPERATION_NOT_FOUND,
                                "Balance operation not found with reference: "
                                        + normalizedOperationReference
                        )
                );
    }

    private BalanceOperation buildBalanceOperation(
            String operationReference,
            String transactionReference,
            Long accountId,
            BalanceOperationType operationType,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            String requestHash,
            String description,
            LocalDateTime createdAt) {

        return BalanceOperation.builder()
                .operationReference(operationReference)
                .transactionReference(transactionReference)
                .accountId(accountId)
                .operationType(operationType)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .requestHash(requestHash)
                .description(
                        normalizeDescription(description)
                )
                .createdAt(createdAt)
                .build();
    }

    private AccountTransferResponse buildTransferResponse(
            String transactionReference,
            BalanceOperation debitOperation,
            BalanceOperation creditOperation) {

        return new AccountTransferResponse(
                transactionReference,
                BalanceOperationResponse.from(
                        debitOperation
                ),
                BalanceOperationResponse.from(
                        creditOperation
                )
        );
    }

    private void validateExistingOperation(
            BalanceOperation existingOperation,
            String requestHash) {

        if (!existingOperation
                .getRequestHash()
                .equals(requestHash)) {

            throw new AccountBusinessException(
                    ErrorCode.BALANCE_OPERATION_IDEMPOTENCY_CONFLICT,
                    "Operation reference was already used "
                            + "with different request data"
            );
        }
    }

    private BigDecimal calculateBalanceAfter(
            Long accountId,
            BigDecimal balanceBefore,
            BalanceOperationType operationType,
            BigDecimal amount) {

        if (operationType
                == BalanceOperationType.DEBIT) {

            if (balanceBefore.compareTo(amount) < 0) {

                throw new AccountBusinessException(
                        ErrorCode.INSUFFICIENT_FUNDS,
                        "Insufficient funds for account ID: "
                                + accountId
                );
            }

            return balanceBefore.subtract(amount);
        }

        if (operationType
                == BalanceOperationType.CREDIT) {

            return balanceBefore.add(amount);
        }

        throw new AccountBusinessException(
                ErrorCode.INVALID_REQUEST,
                "Unsupported balance operation type: "
                        + operationType
        );
    }

    private void validateAccountStatus(
            Account account) {

        if (account.getAccountStatus()
                != AccountStatus.ACTIVE) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_ACCOUNT_STATE,
                    "Balance operation requires an active account. "
                            + "accountId="
                            + account.getAccountId()
            );
        }
    }

    private void validateSupportedOperation(
            BalanceOperationType operationType) {

        if (operationType
                == BalanceOperationType.REVERSAL) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Direct REVERSAL operations are not supported"
            );
        }
    }

    private String validateAndNormalizeOperationReference(
            String operationReference,
            int maximumLength) {

        if (operationReference == null
                || operationReference.isBlank()) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Operation-Reference header is required"
            );
        }

        String normalizedReference =
                operationReference.trim();

        if (normalizedReference.length()
                > maximumLength) {

            throw new AccountBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Operation-Reference must not exceed "
                            + maximumLength
                            + " characters"
            );
        }

        return normalizedReference;
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
                    canonicalRequest.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    ex
            );
        }
    }

    private String normalizeAmount(
            BigDecimal amount) {

        return amount.stripTrailingZeros()
                .toPlainString();
    }

    private String normalizeDescription(
            String description) {

        if (description == null) {
            return "";
        }

        return description.trim();
    }

    private AccountBusinessException accountNotFound(
            Long accountId) {

        return new AccountBusinessException(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found with ID: "
                        + accountId
        );
    }

}