package com.financialplatform.account.mapper;

import com.financialplatform.account.dto.AccountResponse;
import com.financialplatform.account.entity.Account;

public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountResponse toResponse(Account account) {

        return new AccountResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getCustomerId(),
                account.getAccountType().name(),
                account.getBalance(),
                account.getAccountStatus().name(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}