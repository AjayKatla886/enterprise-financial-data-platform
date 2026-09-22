package com.financialplatform.transaction.repository;

import com.financialplatform.transaction.entity.Transaction;
import com.financialplatform.transaction.entity.TransactionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTransactionReference(
            String transactionReference
    );

    Optional<Transaction> findByIdempotencyKey(
            String idempotencyKey
    );

    @Query("""
            SELECT transaction
            FROM Transaction transaction
            WHERE transaction.transactionStatus IN :statuses
              AND transaction.updatedAt < :cutoff
              AND transaction.reconciliationAttempts < :maxAttempts
            ORDER BY transaction.updatedAt ASC
            """)
    List<Transaction> findReconciliationCandidates(

            @Param("statuses")
            Collection<TransactionStatus> statuses,

            @Param("cutoff")
            LocalDateTime cutoff,

            @Param("maxAttempts")
            int maxAttempts,

            Pageable pageable
    );
}