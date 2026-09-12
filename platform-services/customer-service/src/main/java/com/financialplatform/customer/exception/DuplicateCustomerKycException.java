package com.financialplatform.customer.exception;

public class DuplicateCustomerKycException extends RuntimeException {

    public DuplicateCustomerKycException(String message) {
        super(message);
    }
}