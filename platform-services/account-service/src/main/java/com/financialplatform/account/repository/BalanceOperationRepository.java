package com.financialplatform.account.repository;

import com.financialplatform.account.entity.BalanceOperation;
import com.financialplatform.account.entity.BalanceOperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    List<BalanceOperation>
    findByAccountIdOrderByCreatedAtDesc(
            Long accountId
    );

    List<BalanceOperation>
    findByTransactionReferenceOrderByCreatedAtAsc(
            String transactionReference
    );

    /*
     * Finds the most recent ledger operation before the statement
     * period. Its balanceAfter can be used as the period's opening
     * balance when no operation begins exactly at fromDate.
     */
    Optional<BalanceOperation>
    findFirstByAccountIdAndCreatedAtLessThanOrderByCreatedAtDescBalanceOperationIdDesc(
            Long accountId,
            LocalDateTime fromDate
    );

    /*
     * Finds the first operation inside the statement period.
     * Its balanceBefore represents the statement opening balance.
     */
    Optional<BalanceOperation>
    findFirstByAccountIdAndCreatedAtBetweenOrderByCreatedAtAscBalanceOperationIdAsc(
            Long accountId,
            LocalDateTime fromDate,
            LocalDateTime toDate
    );

    /*
     * Finds the final operation inside the statement period.
     * Its balanceAfter represents the statement closing balance.
     */
    Optional<BalanceOperation>
    findFirstByAccountIdAndCreatedAtBetweenOrderByCreatedAtDescBalanceOperationIdDesc(
            Long accountId,
            LocalDateTime fromDate,
            LocalDateTime toDate
    );

    @Query("""
            SELECT COALESCE(SUM(operation.amount), 0)
            FROM BalanceOperation operation
            WHERE operation.accountId = :accountId
              AND operation.operationType = :operationType
              AND operation.createdAt >= :fromDate
              AND operation.createdAt <= :toDate
            """)
    BigDecimal sumAmountByAccountAndTypeAndPeriod(

            @Param("accountId")
            Long accountId,

            @Param("operationType")
            BalanceOperationType operationType,

            @Param("fromDate")
            LocalDateTime fromDate,

            @Param("toDate")
            LocalDateTime toDate
    );

    @Query("""
            SELECT COUNT(operation)
            FROM BalanceOperation operation
            WHERE operation.accountId = :accountId
              AND operation.operationType = :operationType
              AND operation.createdAt >= :fromDate
              AND operation.createdAt <= :toDate
            """)
    long countByAccountAndTypeAndPeriod(

            @Param("accountId")
            Long accountId,

            @Param("operationType")
            BalanceOperationType operationType,

            @Param("fromDate")
            LocalDateTime fromDate,

            @Param("toDate")
            LocalDateTime toDate
    );
}