package com.financialplatform.account.repository;

import com.financialplatform.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.financialplatform.account.entity.AccountType;

import java.util.List;

public interface AccountRepository extends
        JpaRepository<Account, Long>,
        JpaSpecificationExecutor<Account> {

    List<Account> findByCustomerId(Long customerId);

    boolean existsByAccountNumber(String accountNumber);
    boolean existsByCustomerIdAndAccountType(
            Long customerId,
            AccountType accountType
    );

}