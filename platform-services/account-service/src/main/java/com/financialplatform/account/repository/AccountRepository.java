package com.financialplatform.account.repository;

import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends
        JpaRepository<Account, Long>,
        JpaSpecificationExecutor<Account> {

    List<Account> findByCustomerId(Long customerId);

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByCustomerIdAndAccountType(
            Long customerId,
            AccountType accountType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT account
            FROM Account account
            WHERE account.accountId = :accountId
            """)
    Optional<Account> findByIdForUpdate(
            @Param("accountId") Long accountId
    );
}