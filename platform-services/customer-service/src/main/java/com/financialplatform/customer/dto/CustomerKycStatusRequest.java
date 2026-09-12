package com.financialplatform.customer.dto;

import com.financialplatform.customer.entity.KycStatus;
import jakarta.validation.constraints.NotNull;

public record CustomerKycStatusRequest(

        @NotNull(message = "KYC status is required")
        KycStatus kycStatus
) {
}