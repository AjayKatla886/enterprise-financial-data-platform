package com.financialplatform.account.repository;

import com.financialplatform.account.entity.Account;
import com.financialplatform.account.entity.AccountStatus;
import com.financialplatform.account.entity.AccountType;
import org.springframework.data.jpa.domain.Specification;

public final class AccountSpecifications {

    private AccountSpecifications() {
    }

    public static Specification<Account> hasCustomerId(Long customerId) {
        return (root, query, criteriaBuilder) -> {

            if (customerId == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("customerId"),
                    customerId
            );
        };
    }

    public static Specification<Account> hasAccountType(String accountType) {
        return (root, query, criteriaBuilder) -> {

            if (accountType == null || accountType.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            AccountType type;

            try {
                type = AccountType.valueOf(
                        accountType.trim().toUpperCase()
                );
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Invalid account type: " + accountType
                );
            }

            return criteriaBuilder.equal(
                    root.get("accountType"),
                    type
            );
        };
    }

    public static Specification<Account> hasStatus(String status) {
        return (root, query, criteriaBuilder) -> {

            if (status == null || status.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            AccountStatus accountStatus;

            try {
                accountStatus = AccountStatus.valueOf(
                        status.trim().toUpperCase()
                );
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Invalid account status: " + status
                );
            }

            return criteriaBuilder.equal(
                    root.get("accountStatus"),
                    accountStatus
            );
        };
    }
}