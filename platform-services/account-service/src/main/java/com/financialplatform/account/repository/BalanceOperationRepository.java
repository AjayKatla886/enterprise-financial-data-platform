package com.financialplatform.account.repository;

import com.financialplatform.account.entity.BalanceOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface BalanceOperationRepository
        extends JpaRepository<BalanceOperation, Long>,
        JpaSpecificationExecutor<BalanceOperation> {

    Optional<BalanceOperation> findByOperationReference(
            String operationReference
    );

    boolean existsByOperationReference(
            String operationReference
    );

    List<BalanceOperation> findByAccountIdOrderByCreatedAtDesc(
            Long accountId
    );

    List<BalanceOperation>
    findByTransactionReferenceOrderByCreatedAtAsc(
            String transactionReference
    );
}