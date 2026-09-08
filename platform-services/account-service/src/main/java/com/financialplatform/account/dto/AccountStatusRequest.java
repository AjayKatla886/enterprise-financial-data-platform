package com.financialplatform.account.dto;

import com.financialplatform.account.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AccountStatusRequest(

        @NotNull(message = "Account status is required")
        AccountStatus accountStatus

) {
}