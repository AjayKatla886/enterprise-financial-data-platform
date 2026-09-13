package com.financialplatform.account.repository;

import com.financialplatform.account.entity.BalanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BalanceOperationRepository
        extends JpaRepository<BalanceOperation, Long> {

    Optional<BalanceOperation> findByOperationReference(
            String operationReference
    );

    boolean existsByOperationReference(
            String operationReference
    );

    List<BalanceOperation> findByAccountIdOrderByCreatedAtDesc(
            Long accountId
    );

    List<BalanceOperation> findByTransactionReferenceOrderByCreatedAtAsc(
            String transactionReference
    );
}