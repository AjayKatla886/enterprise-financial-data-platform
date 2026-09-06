package com.financialplatform.customer.mapper;

import com.financialplatform.customer.dto.CustomerResponse;
import com.financialplatform.customer.entity.Customer;

public final class CustomerMapper {

    private CustomerMapper() {
    }

    public static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getCustomerId(),
                customer.getCustomerNumber(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getDateOfBirth(),
                customer.getCustomerStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}