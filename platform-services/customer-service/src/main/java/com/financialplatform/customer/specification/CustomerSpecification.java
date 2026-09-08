package com.financialplatform.customer.specification;

import com.financialplatform.customer.entity.Customer;
import org.springframework.data.jpa.domain.Specification;
import com.financialplatform.customer.entity.CustomerStatus;

public final class CustomerSpecification {

    private CustomerSpecification() {
    }

    public static Specification<Customer> hasStatus(String status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null || status.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            CustomerStatus customerStatus;

            try {
                customerStatus =
                        CustomerStatus.valueOf(
                                status.trim().toUpperCase()
                        );
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Invalid customer status: " + status
                );
            }

            return criteriaBuilder.equal(
                    root.get("customerStatus"),
                    customerStatus
            );
        };
    }
    public static Specification<Customer> hasFirstName(String firstName) {
        return (root, query, criteriaBuilder) -> {
            if (firstName == null || firstName.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("firstName")),
                    firstName.trim().toLowerCase() + "%"
            );
        };
    }

    public static Specification<Customer> hasLastName(String lastName) {
        return (root, query, criteriaBuilder) -> {
            if (lastName == null || lastName.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("lastName")),
                    lastName.trim().toLowerCase() + "%"
            );
        };
    }

    public static Specification<Customer> hasCustomerNumber(String customerNumber) {
        return (root, query, criteriaBuilder) -> {
            if (customerNumber == null || customerNumber.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("customerNumber")),
                    customerNumber.trim().toLowerCase()
            );
        };
    }
}