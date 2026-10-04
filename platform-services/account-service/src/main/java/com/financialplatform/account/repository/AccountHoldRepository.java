package com.financialplatform.account.repository;

import com.financialplatform.account.entity.AccountHold;
import com.financialplatform.account.entity.AccountHoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountHoldRepository
        extends JpaRepository<AccountHold, Long> {

    Optional<AccountHold> findByHoldReference(
            String holdReference
    );

    boolean existsByHoldReference(
            String holdReference
    );

    Page<AccountHold> findByAccountId(
            Long accountId,
            Pageable pageable
    );

    List<AccountHold>
    findByTransactionReferenceOrderByCreatedAtAsc(
            String transactionReference
    );

    /*
     * Calculates the total amount reserved by active, unexpired
     * holds. An expired hold must not continue reducing the
     * account's available balance while it waits for the scheduled
     * expiration process to update its status.
     */
    @Query("""
            SELECT COALESCE(SUM(hold.amount), 0)
            FROM AccountHold hold
            WHERE hold.accountId = :accountId
              AND hold.holdStatus = :holdStatus
              AND hold.expiresAt > :calculatedAt
            """)
    BigDecimal sumActiveHoldAmount(

            @Param("accountId")
            Long accountId,

            @Param("holdStatus")
            AccountHoldStatus holdStatus,

            @Param("calculatedAt")
            LocalDateTime calculatedAt
    );

    /*
     * Capture and release workflows lock the hold before changing
     * its status. This prevents two processes from resolving the
     * same hold simultaneously.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT hold
            FROM AccountHold hold
            WHERE hold.holdReference = :holdReference
            """)
    Optional<AccountHold> findByHoldReferenceForUpdate(

            @Param("holdReference")
            String holdReference
    );

    /*
     * Supports the future scheduled process that changes expired
     * ACTIVE holds to EXPIRED.
     */
    @Query("""
            SELECT hold
            FROM AccountHold hold
            WHERE hold.holdStatus = :holdStatus
              AND hold.expiresAt <= :expirationTime
            ORDER BY hold.expiresAt ASC
            """)
    List<AccountHold> findExpiredHolds(

            @Param("holdStatus")
            AccountHoldStatus holdStatus,

            @Param("expirationTime")
            LocalDateTime expirationTime,

            Pageable pageable
    );
}