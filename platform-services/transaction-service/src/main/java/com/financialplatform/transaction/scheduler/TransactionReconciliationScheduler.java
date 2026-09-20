package com.financialplatform.transaction.scheduler;

import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
import com.financialplatform.transaction.repository.TransactionRepository;
import com.financialplatform.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "transaction.reconciliation.enabled",
        havingValue = "true"
)
public class TransactionReconciliationScheduler {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    @Value("${transaction.reconciliation.minimum-age-seconds:30}")
    private long minimumAgeSeconds;

    @Value("${transaction.reconciliation.batch-size:50}")
    private int batchSize;

    @Value("${transaction.reconciliation.max-attempts:5}")
    private int maxAttempts;

    @Scheduled(
            fixedDelayString =
                    "${transaction.reconciliation.fixed-delay-ms:60000}",
            initialDelayString =
                    "${transaction.reconciliation.initial-delay-ms:30000}"
    )
    public void reconcileUnresolvedTransactions() {

        LocalDateTime cutoff =
                LocalDateTime.now()
                        .minusSeconds(minimumAgeSeconds);

        List<Transaction> candidates =
                transactionRepository
                        .findReconciliationCandidates(
                                List.of(
                                        TransactionStatus.PROCESSING,
                                        TransactionStatus
                                                .RECONCILIATION_REQUIRED
                                ),
                                cutoff,
                                maxAttempts,
                                PageRequest.of(0, batchSize)
                        );

        if (candidates.isEmpty()) {
            log.debug(
                    "No transactions eligible for reconciliation"
            );

            return;
        }

        log.info(
                "Automatic transaction reconciliation started. "
                        + "candidateCount={}",
                candidates.size()
        );

        for (Transaction transaction : candidates) {
            reconcileCandidate(transaction);
        }

        log.info(
                "Automatic transaction reconciliation completed. "
                        + "candidateCount={}",
                candidates.size()
        );
    }

    private void reconcileCandidate(
            Transaction transaction) {

        int currentAttempts =
                transaction.getReconciliationAttempts() == null
                        ? 0
                        : transaction.getReconciliationAttempts();

        int nextAttempt =
                currentAttempts + 1;

        transaction.setReconciliationAttempts(
                nextAttempt
        );

        transaction.setLastReconciliationAt(
                LocalDateTime.now()
        );

        transactionRepository.saveAndFlush(transaction);

        try {

            Transaction result =
                    transactionService
                            .reconcileTransaction(
                                    transaction
                                            .getTransactionReference()
                            );

            if (result.getTransactionStatus()
                    == TransactionStatus.COMPLETED) {

                log.info(
                        "Automatic reconciliation confirmed transaction. "
                                + "transactionId={}, reference={}, attempt={}",
                        result.getTransactionId(),
                        result.getTransactionReference(),
                        nextAttempt
                );

                return;
            }

            if (result.getTransactionStatus()
                    == TransactionStatus
                    .RECONCILIATION_REQUIRED
                    && nextAttempt >= maxAttempts) {

                moveToManualReview(result);

                return;
            }

            log.warn(
                    "Transaction remains unresolved after reconciliation. "
                            + "transactionId={}, reference={}, attempt={}",
                    result.getTransactionId(),
                    result.getTransactionReference(),
                    nextAttempt
            );

        } catch (RuntimeException ex) {

            log.error(
                    "Unexpected automatic reconciliation failure. "
                            + "transactionId={}, reference={}, attempt={}",
                    transaction.getTransactionId(),
                    transaction.getTransactionReference(),
                    nextAttempt,
                    ex
            );

            if (nextAttempt >= maxAttempts) {
                moveToManualReview(transaction);
            }
        }
    }

    private void moveToManualReview(
            Transaction transaction) {

        transaction.setTransactionStatus(
                TransactionStatus.MANUAL_REVIEW
        );

        transaction.setFailureReason(
                "Automatic reconciliation attempts exhausted; "
                        + "manual review is required"
        );

        transaction.setUpdatedAt(
                LocalDateTime.now()
        );

        transactionRepository.saveAndFlush(transaction);

        log.error(
                "Transaction moved to manual review. "
                        + "transactionId={}, reference={}, attempts={}",
                transaction.getTransactionId(),
                transaction.getTransactionReference(),
                transaction.getReconciliationAttempts()
        );
    }
}