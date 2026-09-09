package com.financialplatform.customer.mapper;

import com.financialplatform.customer.dto.CustomerAddressResponse;
import com.financialplatform.customer.entity.CustomerAddress;

public final class CustomerAddressMapper {

    private CustomerAddressMapper() {
    }

    public static CustomerAddressResponse toResponse(
            CustomerAddress address) {

        return new CustomerAddressResponse(
                address.getAddressId(),
                address.getCustomerId(),
                address.getAddressType().name(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.isCurrent(),
                address.getValidFrom(),
                address.getValidTo(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}