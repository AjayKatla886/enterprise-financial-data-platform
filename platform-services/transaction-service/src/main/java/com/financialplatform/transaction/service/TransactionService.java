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

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;

    public Transaction submitTransaction(
            String idempotencyKey,
            TransactionRequest request) {

        String normalizedKey =
                validateAndNormalizeIdempotencyKey(idempotencyKey);

        String requestHash = generateRequestHash(request);

        /*
         * Check whether this idempotency key was already used.
         */
        Transaction existingTransaction = transactionRepository
                .findByIdempotencyKey(normalizedKey)
                .orElse(null);

        if (existingTransaction != null) {
            return handleExistingTransaction(
                    existingTransaction,
                    requestHash
            );
        }

        /*
         * Validate accounts only for a new transaction.
         * Replayed requests should not call Account Service again.
         */
        if (request.sourceAccountId() != null) {
            validateActiveAccount(request.sourceAccountId());
        }

        if (request.targetAccountId() != null) {
            validateActiveAccount(request.targetAccountId());
        }

        LocalDateTime now = LocalDateTime.now();

        Transaction transaction = Transaction.builder()
                .transactionReference(UUID.randomUUID().toString())
                .idempotencyKey(normalizedKey)
                .requestHash(requestHash)
                .transactionType(request.transactionType())
                .sourceAccountId(request.sourceAccountId())
                .targetAccountId(request.targetAccountId())
                .amount(request.amount())
                .currency(normalizeCurrency(request.currency()))
                .transactionStatus(TransactionStatus.PENDING)
                .description(normalizeDescription(request.description()))
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            Transaction savedTransaction =
                    transactionRepository.saveAndFlush(transaction);

            log.info(
                    "Transaction request recorded. transactionId={}, "
                            + "reference={}, idempotencyKey={}",
                    savedTransaction.getTransactionId(),
                    savedTransaction.getTransactionReference(),
                    normalizedKey
            );

            return savedTransaction;

        } catch (DataIntegrityViolationException ex) {

            /*
             * Handles two concurrent requests using the same key.
             * The database unique constraint allows only one insert.
             */
            Transaction concurrentTransaction = transactionRepository
                    .findByIdempotencyKey(normalizedKey)
                    .orElseThrow(() -> ex);

            return handleExistingTransaction(
                    concurrentTransaction,
                    requestHash
            );
        }
    }

    public Transaction getTransactionByReference(
            String transactionReference) {

        return transactionRepository
                .findByTransactionReference(transactionReference)
                .orElseThrow(() -> new TransactionBusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "Transaction not found with reference: "
                                + transactionReference
                ));
    }

    private Transaction handleExistingTransaction(
            Transaction existingTransaction,
            String requestHash) {

        if (!requestHash.equals(existingTransaction.getRequestHash())) {

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
                        + "transactionId={}, reference={}, idempotencyKey={}",
                existingTransaction.getTransactionId(),
                existingTransaction.getTransactionReference(),
                existingTransaction.getIdempotencyKey()
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

        String normalizedKey = idempotencyKey.trim();

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

        String canonicalRequest = String.join(
                "|",
                value(request.transactionType()),
                value(request.sourceAccountId()),
                value(request.targetAccountId()),
                normalizeAmount(request.amount()),
                normalizeCurrency(request.currency()),
                value(normalizeDescription(request.description()))
        );

        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = messageDigest.digest(
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

        if (amount == null) {
            return "<null>";
        }

        return amount.stripTrailingZeros().toPlainString();
    }

    private String normalizeCurrency(String currency) {

        if (currency == null) {
            return "<null>";
        }

        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDescription(String description) {

        if (description == null) {
            return null;
        }

        return description.trim();
    }

    private String value(Object value) {
        return value == null
                ? "<null>"
                : value.toString();
    }

    private void validateActiveAccount(Long accountId) {

        AccountClient.AccountLookupResponse account =
                accountClient.getAccountById(accountId);

        if (!"ACTIVE".equalsIgnoreCase(
                account.accountStatus())) {

            throw new TransactionBusinessException(
                    ErrorCode.TRANSACTION_ACCOUNT_NOT_ACTIVE,
                    "Transaction requires an active account. accountId="
                            + accountId
            );
        }
    }
}