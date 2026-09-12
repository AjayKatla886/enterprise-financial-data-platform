package com.financialplatform.customer.exception;

public class CustomerKycNotFoundException extends RuntimeException {

    public CustomerKycNotFoundException(String message) {
        super(message);
    }
}