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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;

    public Transaction submitTransaction(TransactionRequest request) {

        if (request.sourceAccountId() != null) {
            validateActiveAccount(request.sourceAccountId());
        }

        if (request.targetAccountId() != null) {
            validateActiveAccount(request.targetAccountId());
        }

        LocalDateTime now = LocalDateTime.now();

        Transaction transaction = Transaction.builder()
                .transactionReference(UUID.randomUUID().toString())
                .transactionType(request.transactionType())
                .sourceAccountId(request.sourceAccountId())
                .targetAccountId(request.targetAccountId())
                .amount(request.amount())
                .currency(request.currency())
                .transactionStatus(TransactionStatus.PENDING)
                .description(request.description())
                .createdAt(now)
                .updatedAt(now)
                .build();

        Transaction savedTransaction =
                transactionRepository.saveAndFlush(transaction);

        log.info(
                "Transaction request recorded. transactionId={}, reference={}",
                savedTransaction.getTransactionId(),
                savedTransaction.getTransactionReference()
        );

        return savedTransaction;
    }

    public Transaction getTransactionByReference(String transactionReference) {

        return transactionRepository
                .findByTransactionReference(transactionReference)
                .orElseThrow(() -> new TransactionBusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND,
                        "Transaction not found with reference: "
                                + transactionReference
                ));
    }

    private void validateActiveAccount(Long accountId) {

        AccountClient.AccountLookupResponse account =
                accountClient.getAccountById(accountId);

        if (!"ACTIVE".equalsIgnoreCase(account.accountStatus())) {
            throw new TransactionBusinessException(
                    ErrorCode.TRANSACTION_ACCOUNT_NOT_ACTIVE,
                    "Transaction requires an active account. accountId="
                            + accountId
            );
        }
    }
}