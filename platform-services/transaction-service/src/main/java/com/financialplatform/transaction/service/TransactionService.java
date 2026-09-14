package com.financialplatform.transaction.service;

import com.financialplatform.common.exception.ErrorCode;
import com.financialplatform.transaction.client.AccountClient;
import com.financialplatform.transaction.dto.TransactionRequest;
import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
import com.financialplatform.transaction.exception.TransactionBusinessException;
import com.financialplatform.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;

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

            /*
             * A PENDING transaction may exist when Transaction Service
             * stopped after recording it but before processing completed.
             *
             * Retrying is safe because Account Service balance and transfer
             * operations use deterministic idempotency references.
             */
            if (idempotentTransaction.getTransactionStatus()
                    == TransactionStatus.PENDING) {

                return executeTransaction(
                        idempotentTransaction
                );
            }

            return idempotentTransaction;
        }

        /*
         * Validate referenced accounts before storing a new transaction.
         */
        validateReferencedAccounts(request);

        LocalDateTime now =
                LocalDateTime.now();

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
             * First database save records the transaction as PENDING.
             */
            savedTransaction =
                    transactionRepository
                            .saveAndFlush(transaction);

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
             * Handles simultaneous requests using the same
             * transaction idempotency key.
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

        return transactionRepository
                .findByTransactionReference(
                        transactionReference
                )
                .orElseThrow(() ->
                        new TransactionBusinessException(
                                ErrorCode.TRANSACTION_NOT_FOUND,
                                "Transaction not found with reference: "
                                        + transactionReference
                        )
                );
    }

    private Transaction executeTransaction(
            Transaction transaction) {

        try {
            switch (transaction.getTransactionType()) {

                case DEPOSIT ->
                        executeDeposit(transaction);

                case WITHDRAWAL ->
                        executeWithdrawal(transaction);

                case TRANSFER ->
                        executeTransfer(transaction);
            }

            transaction.setTransactionStatus(
                    TransactionStatus.COMPLETED
            );

            transaction.setFailureReason(null);
            transaction.setUpdatedAt(
                    LocalDateTime.now()
            );

            /*
             * Second database save records the successful result.
             */
            Transaction completedTransaction =
                    transactionRepository
                            .saveAndFlush(transaction);

            log.info(
                    "Transaction completed successfully. "
                            + "transactionId={}, reference={}, type={}",
                    completedTransaction.getTransactionId(),
                    completedTransaction.getTransactionReference(),
                    completedTransaction.getTransactionType()
            );

            return completedTransaction;

        } catch (RuntimeException ex) {

            markTransactionFailed(
                    transaction,
                    ex.getMessage()
            );

            throw ex;
        }
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
         * Account Service executes the source debit, target credit,
         * and two ledger inserts in one local database transaction.
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

    private void markTransactionFailed(
            Transaction transaction,
            String failureReason) {

        transaction.setTransactionStatus(
                TransactionStatus.FAILED
        );

        transaction.setFailureReason(
                normalizeFailureReason(
                        failureReason
                )
        );

        transaction.setUpdatedAt(
                LocalDateTime.now()
        );

        transactionRepository
                .saveAndFlush(transaction);

        log.warn(
                "Transaction failed. "
                        + "transactionId={}, reference={}, reason={}",
                transaction.getTransactionId(),
                transaction.getTransactionReference(),
                transaction.getFailureReason()
        );
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
                accountClient
                        .getAccountById(accountId);

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