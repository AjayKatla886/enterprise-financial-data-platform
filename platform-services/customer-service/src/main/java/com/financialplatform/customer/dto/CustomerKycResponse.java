package com.financialplatform.customer.dto;

import java.time.LocalDateTime;

public record CustomerKycResponse(

        Long kycId,
        Long customerId,
        String kycStatus,
        String documentType,
        String documentNumber,
        LocalDateTime verifiedAt,
        LocalDateTime lastReviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}