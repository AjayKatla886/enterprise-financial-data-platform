package com.financialplatform.customer.mapper;

import com.financialplatform.customer.dto.CustomerKycResponse;
import com.financialplatform.customer.entity.CustomerKyc;

public final class CustomerKycMapper {

    private CustomerKycMapper() {
    }

    public static CustomerKycResponse toResponse(CustomerKyc kyc) {

        return new CustomerKycResponse(
                kyc.getKycId(),
                kyc.getCustomerId(),
                kyc.getKycStatus().name(),
                kyc.getDocumentType().name(),
                kyc.getDocumentNumber(),
                kyc.getVerifiedAt(),
                kyc.getLastReviewedAt(),
                kyc.getCreatedAt(),
                kyc.getUpdatedAt()
        );
    }
}