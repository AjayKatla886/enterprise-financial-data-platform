package com.financialplatform.transaction.service;

import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.transaction.client.AccountClient;
import com.financialplatform.transaction.dto.TransactionRequest;
import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
import com.financialplatform.transaction.entity.TransactionTransitionSource;
import com.financialplatform.transaction.entity.TransactionType;
import com.financialplatform.transaction.exception.AccountServiceUnavailableException;
import com.financialplatform.transaction.exception.TransactionBusinessException;
import com.financialplatform.transaction.repository.TransactionRepository;
import com.financialplatform.transaction.specification.TransactionSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of(
                    "transactionId",
                    "transactionReference",
                    "transactionType",
                    "transactionStatus",
                    "amount",
                    "createdAt",
                    "updatedAt"
            );

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;

    private final TransactionStatusTransitionService
            transactionStatusTransitionService;

    public Page<Transaction> getTransactions(
            Long accountId,
            Long customerId,
            TransactionType transactionType,
            TransactionStatus transactionStatus,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        validateTransactionSearch(
                accountId,
                customerId,
                fromDate,
                toDate,
                sortBy,
                sortDir
        );

        Sort.Direction direction =
                "desc".equalsIgnoreCase(sortDir)
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;

        PageRequest pageRequest =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(direction, sortBy)
                );

        List<Long> accountIds = List.of();

        if (accountId != null) {

            accountClient.getAccountById(accountId);

            accountIds = List.of(accountId);
        }

        if (customerId != null) {

            accountIds =
                    accountClient
                            .getAccountsByCustomerId(customerId)
                            .stream()
                            .map(
                                    AccountClient
                                            .AccountLookupResponse::accountId
                            )
                            .distinct()
                            .toList();

            if (accountIds.isEmpty()) {
                return Page.empty(pageRequest);
            }
        }

        return transactionRepository.findAll(
                TransactionSpecification.withFilters(
                        accountIds,
                        transactionType,
                        transactionStatus,
                        fromDate,
                        toDate
                ),
                pageRequest
        );
    }

    private void validateTransactionSearch(
            Long accountId,
            Long customerId,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            String sortBy,
            String sortDir) {

        if (accountId != null && customerId != null) {
            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "accountId and customerId cannot be used together"
            );
        }

        if (accountId != null && accountId <= 0) {
            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Account ID must be greater than zero"
            );
        }

        if (customerId != null && customerId <= 0) {
            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Customer ID must be greater than zero"
            );
        }

        if (fromDate != null
                && toDate != null
                && fromDate.isAfter(toDate)) {

            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "fromDate must be before or equal to toDate"
            );
        }

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Unsupported transaction sort field: " + sortBy
            );
        }

        if (!"asc".equalsIgnoreCase(sortDir)
                && !"desc".equalsIgnoreCase(sortDir)) {

            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "sortDir must be either asc or desc"
            );
        }
    }

    public Transaction submitTransaction(
            String idempotencyKey,
            TransactionRequest request) {

        String normalizedKey =
                validateAndNormalizeIdempotencyKey(
                        idempotencyKey
                );

        String requestHash =
                generateRequestHash(request);

        Transaction existingTransaction =
                transactionRepository
                        .findByIdempotencyKey(normalizedKey)
                        .orElse(null);

        if (existingTransaction != null) {

            Transaction idempotentTransaction =
                    handleExistingTransaction(
                            existingTransaction,
                            requestHash
                    );

            if (idempotentTransaction.getTransactionStatus()
                    == TransactionStatus.PENDING) {

                return executeTransaction(
                        idempotentTransaction
                );
            }

            return idempotentTransaction;
        }

        validateReferencedAccounts(request);

        LocalDateTime now = LocalDateTime.now();

        Transaction transaction =
                Transaction.builder()
                        .transactionReference(
                                UUID.randomUUID().toString()
                        )
                        .idempotencyKey(normalizedKey)
                        .requestHash(requestHash)
                        .transactionType(
                                request.transactionType()
                        )
                        .sourceAccountId(
                                request.sourceAccountId()
                        )
                        .targetAccountId(
                                request.targetAccountId()
                        )
                        .amount(request.amount())
                        .currency(
                                normalizeCurrency(
                                        request.currency()
                                )
                        )
                        .transactionStatus(
                                TransactionStatus.PENDING
                        )
                        .description(
                                normalizeDescription(
                                        request.description()
                                )
                        )
                        .failureReason(null)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        Transaction savedTransaction;

        try {

            /*
             * The transaction and its initial null -> PENDING
             * history record are committed together.
             */
            savedTransaction =
                    transactionStatusTransitionService
                            .saveInitialTransaction(
                                    transaction,
                                    TransactionTransitionSource
                                            .TRANSACTION_SUBMISSION,
                                    "Transaction request recorded"
                            );

            log.info(
                    "Transaction request recorded. "
                            + "transactionId={}, reference={}, "
                            + "idempotencyKey={}",
                    savedTransaction.getTransactionId(),
                    savedTransaction.getTransactionReference(),
                    normalizedKey
            );

        } catch (DataIntegrityViolationException ex) {

            /*
             * The unique database constraint protects against two
             * simultaneous requests using the same idempotency key.
             */
            Transaction concurrentTransaction =
                    transactionRepository
                            .findByIdempotencyKey(normalizedKey)
                            .orElseThrow(() -> ex);

            Transaction idempotentTransaction =
                    handleExistingTransaction(
                            concurrentTransaction,
                            requestHash
                    );

            if (idempotentTransaction.getTransactionStatus()
                    == TransactionStatus.PENDING) {

                return executeTransaction(
                        idempotentTransaction
                );
            }

            return idempotentTransaction;
        }

        return executeTransaction(savedTransaction);
    }

    public Transaction getTransactionByReference(
            String transactionReference) {

        if (transactionReference == null
                || transactionReference.isBlank()) {

            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Transaction reference is required"
            );
        }

        String normalizedReference =
                transactionReference.trim();

        return transactionRepository
                .findByTransactionReference(normalizedReference)
                .orElseThrow(() ->
                        new TransactionBusinessException(
                                ErrorCode.TRANSACTION_NOT_FOUND,
                                "Transaction not found with reference: "
                                        + normalizedReference
                        )
                );
    }

    public Transaction reconcileTransaction(
            String transactionReference) {

        return reconcileTransaction(
                transactionReference,
                TransactionTransitionSource.RECONCILIATION_API
        );
    }

    public Transaction reconcileTransaction(
            String transactionReference,
            TransactionTransitionSource source) {

        Transaction transaction =
                getTransactionByReference(
                        transactionReference
                );

        if (transaction.getTransactionStatus()
                == TransactionStatus.COMPLETED
                || transaction.getTransactionStatus()
                == TransactionStatus.FAILED
                || transaction.getTransactionStatus()
                == TransactionStatus.MANUAL_REVIEW) {

            return transaction;
        }

        if (transaction.getTransactionStatus()
                == TransactionStatus.PENDING) {

            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "A PENDING transaction cannot be reconciled"
            );
        }

        try {

            boolean operationCompleted =
                    verifyAccountOperationExists(
                            transaction
                    );

            if (operationCompleted) {

                log.info(
                        "Reconciliation confirmed completed transaction. "
                                + "transactionId={}, reference={}",
                        transaction.getTransactionId(),
                        transaction.getTransactionReference()
                );

                return markTransactionCompleted(
                        transaction,
                        source
                );
            }

            return markTransactionForReconciliation(
                    transaction,
                    "Account balance operation was not found; "
                            + "manual reconciliation is still required",
                    source
            );

        } catch (AccountServiceUnavailableException ex) {

            return markTransactionForReconciliation(
                    transaction,
                    ex.getMessage(),
                    source
            );
        }
    }

    private boolean verifyAccountOperationExists(
            Transaction transaction) {

        String transactionReference =
                transaction.getTransactionReference();

        return switch (transaction.getTransactionType()) {

            case DEPOSIT ->
                    accountClient.balanceOperationExists(
                            transactionReference + "-credit"
                    );

            case WITHDRAWAL ->
                    accountClient.balanceOperationExists(
                            transactionReference + "-debit"
                    );

            case TRANSFER ->
                    accountClient.transferOperationExists(
                            transactionReference + "-transfer"
                    );
        };
    }

    private Transaction executeTransaction(
            Transaction transaction) {

        /*
         * PROCESSING is committed before calling Account Service.
         * If the service stops after this point, reconciliation can
         * safely identify the unresolved transaction.
         */
        markTransactionProcessing(transaction);

        try {

            switch (transaction.getTransactionType()) {

                case DEPOSIT ->
                        executeDeposit(transaction);

                case WITHDRAWAL ->
                        executeWithdrawal(transaction);

                case TRANSFER ->
                        executeTransfer(transaction);
            }

        } catch (AccountServiceUnavailableException ex) {

            /*
             * Account Service may have committed the operation before
             * its HTTP response was lost. Therefore, the transaction
             * outcome is uncertain and requires reconciliation.
             */
            return markTransactionForReconciliation(
                    transaction,
                    ex.getMessage(),
                    TransactionTransitionSource
                            .TRANSACTION_PROCESSOR
            );

        } catch (RuntimeException ex) {

            markTransactionFailed(
                    transaction,
                    ex.getMessage()
            );

            throw ex;
        }

        return markTransactionCompleted(
                transaction,
                TransactionTransitionSource.TRANSACTION_PROCESSOR
        );
    }

    private void executeDeposit(
            Transaction transaction) {

        String operationReference =
                transaction.getTransactionReference()
                        + "-credit";

        accountClient.applyBalanceOperation(
                transaction.getTargetAccountId(),
                operationReference,
                transaction.getTransactionReference(),
                "CREDIT",
                transaction.getAmount(),
                "Deposit transaction "
                        + transaction.getTransactionReference()
        );
    }

    private void executeWithdrawal(
            Transaction transaction) {

        String operationReference =
                transaction.getTransactionReference()
                        + "-debit";

        accountClient.applyBalanceOperation(
                transaction.getSourceAccountId(),
                operationReference,
                transaction.getTransactionReference(),
                "DEBIT",
                transaction.getAmount(),
                "Withdrawal transaction "
                        + transaction.getTransactionReference()
        );
    }

    private void executeTransfer(
            Transaction transaction) {

        String operationReference =
                transaction.getTransactionReference()
                        + "-transfer";

        /*
         * Account Service performs the debit, credit and ledger
         * inserts in one local database transaction.
         */
        accountClient.applyTransfer(
                operationReference,
                transaction.getTransactionReference(),
                transaction.getSourceAccountId(),
                transaction.getTargetAccountId(),
                transaction.getAmount(),
                "Transfer transaction "
                        + transaction.getTransactionReference()
        );
    }

    private void markTransactionProcessing(
            Transaction transaction) {

        transaction.setFailureReason(null);

        transactionStatusTransitionService.transition(
                transaction,
                TransactionStatus.PROCESSING,
                TransactionTransitionSource.TRANSACTION_PROCESSOR,
                "Transaction execution started"
        );

        log.info(
                "Transaction processing started. "
                        + "transactionId={}, reference={}, type={}",
                transaction.getTransactionId(),
                transaction.getTransactionReference(),
                transaction.getTransactionType()
        );
    }

    private Transaction markTransactionCompleted(
            Transaction transaction,
            TransactionTransitionSource source) {

        transaction.setFailureReason(null);

        Transaction completedTransaction =
                transactionStatusTransitionService.transition(
                        transaction,
                        TransactionStatus.COMPLETED,
                        source,
                        "Transaction completed successfully"
                );

        log.info(
                "Transaction completed successfully. "
                        + "transactionId={}, reference={}, type={}",
                completedTransaction.getTransactionId(),
                completedTransaction.getTransactionReference(),
                completedTransaction.getTransactionType()
        );

        return completedTransaction;
    }

    private void markTransactionFailed(
            Transaction transaction,
            String failureReason) {

        String normalizedReason =
                normalizeFailureReason(
                        failureReason
                );

        transaction.setFailureReason(normalizedReason);

        transactionStatusTransitionService.transition(
                transaction,
                TransactionStatus.FAILED,
                TransactionTransitionSource.TRANSACTION_PROCESSOR,
                normalizedReason
        );

        log.warn(
                "Transaction failed. "
                        + "transactionId={}, reference={}, reason={}",
                transaction.getTransactionId(),
                transaction.getTransactionReference(),
                transaction.getFailureReason()
        );
    }

    private Transaction markTransactionForReconciliation(
            Transaction transaction,
            String failureReason,
            TransactionTransitionSource source) {

        String normalizedReason =
                normalizeFailureReason(
                        failureReason
                );

        transaction.setFailureReason(normalizedReason);

        Transaction reconciliationTransaction =
                transactionStatusTransitionService.transition(
                        transaction,
                        TransactionStatus.RECONCILIATION_REQUIRED,
                        source,
                        normalizedReason
                );

        log.error(
                "Transaction requires reconciliation. "
                        + "transactionId={}, reference={}, reason={}",
                reconciliationTransaction.getTransactionId(),
                reconciliationTransaction.getTransactionReference(),
                reconciliationTransaction.getFailureReason()
        );

        return reconciliationTransaction;
    }

    private void validateReferencedAccounts(
            TransactionRequest request) {

        if (request.sourceAccountId() != null) {
            validateActiveAccount(
                    request.sourceAccountId()
            );
        }

        if (request.targetAccountId() != null) {
            validateActiveAccount(
                    request.targetAccountId()
            );
        }
    }

    private Transaction handleExistingTransaction(
            Transaction existingTransaction,
            String requestHash) {

        if (!requestHash.equals(
                existingTransaction.getRequestHash())) {

            log.warn(
                    "Idempotency key reused with different request. "
                            + "idempotencyKey={}, transactionId={}",
                    existingTransaction.getIdempotencyKey(),
                    existingTransaction.getTransactionId()
            );

            throw new TransactionBusinessException(
                    ErrorCode.TRANSACTION_IDEMPOTENCY_CONFLICT,
                    "Idempotency key has already been used "
                            + "for a different transaction request"
            );
        }

        log.info(
                "Returning existing idempotent transaction. "
                        + "transactionId={}, reference={}, "
                        + "idempotencyKey={}, status={}",
                existingTransaction.getTransactionId(),
                existingTransaction.getTransactionReference(),
                existingTransaction.getIdempotencyKey(),
                existingTransaction.getTransactionStatus()
        );

        return existingTransaction;
    }

    private String validateAndNormalizeIdempotencyKey(
            String idempotencyKey) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {

            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Idempotency-Key header is required"
            );
        }

        String normalizedKey =
                idempotencyKey.trim();

        if (normalizedKey.length()
                > MAX_IDEMPOTENCY_KEY_LENGTH) {

            throw new TransactionBusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Idempotency-Key must not exceed "
                            + MAX_IDEMPOTENCY_KEY_LENGTH
                            + " characters"
            );
        }

        return normalizedKey;
    }

    private String generateRequestHash(
            TransactionRequest request) {

        String canonicalRequest =
                String.join(
                        "|",
                        value(request.transactionType()),
                        value(request.sourceAccountId()),
                        value(request.targetAccountId()),
                        normalizeAmount(request.amount()),
                        normalizeCurrency(request.currency()),
                        value(
                                normalizeDescription(
                                        request.description()
                                )
                        )
                );

        try {

            MessageDigest messageDigest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    messageDigest.digest(
                            canonicalRequest.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    ex
            );
        }
    }

    private String normalizeAmount(
            BigDecimal amount) {

        if (amount == null) {
            return "<null>";
        }

        return amount.stripTrailingZeros()
                .toPlainString();
    }

    private String normalizeCurrency(
            String currency) {

        if (currency == null) {
            return "<null>";
        }

        return currency.trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeDescription(
            String description) {

        if (description == null) {
            return null;
        }

        return description.trim();
    }

    private String normalizeFailureReason(
            String failureReason) {

        if (failureReason == null
                || failureReason.isBlank()) {

            return "Transaction processing failed";
        }

        String normalizedReason =
                failureReason.trim();

        if (normalizedReason.length()
                <= MAX_FAILURE_REASON_LENGTH) {

            return normalizedReason;
        }

        return normalizedReason.substring(
                0,
                MAX_FAILURE_REASON_LENGTH
        );
    }

    private String value(
            Object value) {

        return value == null
                ? "<null>"
                : value.toString();
    }

    private void validateActiveAccount(
            Long accountId) {

        AccountClient.AccountLookupResponse account =
                accountClient.getAccountById(accountId);

        if (!"ACTIVE".equalsIgnoreCase(
                account.accountStatus())) {

            throw new TransactionBusinessException(
                    ErrorCode.TRANSACTION_ACCOUNT_NOT_ACTIVE,
                    "Transaction requires an active account. "
                            + "accountId="
                            + accountId
            );
        }
    }
}