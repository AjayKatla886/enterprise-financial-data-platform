package com.financialplatform.customer.dto;

import java.time.LocalDateTime;

public record CustomerAddressResponse(

        Long addressId,
        Long customerId,
        String addressType,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        boolean current,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {
}